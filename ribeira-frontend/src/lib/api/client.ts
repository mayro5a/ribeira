import type { z } from "zod";

/**
 * Cliente HTTP da API. Conectividade é inferida pelo resultado real das
 * requisições (não por `navigator.onLine`): falha de rede vira `NetworkError`,
 * resposta do servidor vira `ApiError` com o `code` estável do Problem Details.
 */

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
const DEFAULT_TIMEOUT_MS = 20_000;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly correlationId?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }

  get retryable(): boolean {
    return this.status >= 500 || this.status === 429;
  }
}

export class NetworkError extends Error {
  constructor(message = "Sem conexão com o servidor.") {
    super(message);
    this.name = "NetworkError";
  }
}

export interface TokenSource {
  accessToken(): string | null;
  /** Renova a sessão; retorna o novo token ou null se for preciso entrar de novo. */
  refresh(): Promise<string | null>;
}

export interface RequestOptions<T> {
  body?: unknown;
  schema?: z.ZodType<T>;
  headers?: Record<string, string>;
  timeoutMs?: number;
  /** Envia cookies (somente rotas de autenticação). */
  withCredentials?: boolean;
  /** Evita recursão ao chamar a própria renovação. */
  skipRefresh?: boolean;
}

type Fetch = typeof fetch;

export class ApiClient {
  constructor(
    private readonly tokens: TokenSource,
    private readonly fetchImpl: Fetch = (...args) => fetch(...args),
    private readonly baseUrl: string = API_BASE_URL,
  ) {}

  get<T>(path: string, options: RequestOptions<T> = {}): Promise<T> {
    return this.request("GET", path, options);
  }

  post<T>(path: string, body: unknown, options: RequestOptions<T> = {}): Promise<T> {
    return this.request("POST", path, { ...options, body });
  }

  put<T>(path: string, body: unknown, options: RequestOptions<T> = {}): Promise<T> {
    return this.request("PUT", path, { ...options, body });
  }

  async request<T>(method: string, path: string, options: RequestOptions<T>): Promise<T> {
    let response = await this.send(method, path, options, this.tokens.accessToken());
    if (response.status === 401 && !options.skipRefresh) {
      const renewed = await this.tokens.refresh();
      if (renewed) {
        response = await this.send(method, path, options, renewed);
      }
    }
    if (!response.ok) {
      throw await toApiError(response);
    }
    if (response.status === 204) {
      return undefined as T;
    }
    const json: unknown = await response.json();
    return options.schema ? options.schema.parse(json) : (json as T);
  }

  private async send<T>(
    method: string,
    path: string,
    options: RequestOptions<T>,
    token: string | null,
  ): Promise<Response> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), options.timeoutMs ?? DEFAULT_TIMEOUT_MS);
    const headers: Record<string, string> = { Accept: "application/json", ...options.headers };
    if (options.body !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    try {
      return await this.fetchImpl(`${this.baseUrl}${path}`, {
        method,
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        signal: controller.signal,
        credentials: options.withCredentials ? "include" : "omit",
        cache: "no-store",
      });
    } catch {
      throw new NetworkError();
    } finally {
      clearTimeout(timer);
    }
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let code = `HTTP_${response.status}`;
  let message = "Não foi possível concluir a operação.";
  let correlationId: string | undefined;
  try {
    const problem = (await response.json()) as {
      code?: string;
      detail?: string;
      correlationId?: string;
    };
    code = problem.code ?? code;
    message = problem.detail ?? message;
    correlationId = problem.correlationId;
  } catch {
    // Resposta sem corpo JSON (ex.: proxy): mantém a mensagem genérica.
  }
  return new ApiError(response.status, code, message, correlationId);
}

/** Mensagem segura para exibir ao usuário a partir de qualquer erro. */
export function describeError(error: unknown): string {
  if (error instanceof NetworkError) {
    return "Sem conexão com o servidor. Os dados ficam salvos no aparelho.";
  }
  if (error instanceof ApiError) {
    return error.message;
  }
  return "Ocorreu um erro inesperado.";
}
