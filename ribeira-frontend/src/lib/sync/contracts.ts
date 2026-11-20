import { z } from "zod";
import { SYNC_ENTITY_SCHEMAS } from "@/lib/api/schemas";

/** Respostas de /api/v1/sync/* validadas em tempo de execução. */

export const PUSH_OUTCOMES = ["APPLIED", "DUPLICATE", "REJECTED", "CONFLICT", "DISCARDED"] as const;
export type PushOutcome = (typeof PUSH_OUTCOMES)[number];

export const pushResultSchema = z.object({
  operationId: z.uuid(),
  outcome: z.enum(PUSH_OUTCOMES),
  serverSeq: z.number().int().nonnegative().nullish(),
  errorCode: z.string().nullish(),
  message: z.string().nullish(),
  conflictId: z.uuid().nullish(),
});
export type PushResult = z.infer<typeof pushResultSchema>;

export const pushResponseSchema = z.object({ results: z.array(pushResultSchema) });

export const MAX_OPERATIONS_PER_PUSH = 25;

const entityTypes = Object.keys(SYNC_ENTITY_SCHEMAS) as [
  keyof typeof SYNC_ENTITY_SCHEMAS,
  ...(keyof typeof SYNC_ENTITY_SCHEMAS)[],
];

export const pullChangeSchema = z.object({
  seq: z.number().int().positive(),
  entityType: z.enum(entityTypes),
  entityId: z.uuid(),
  communityId: z.uuid().nullish(),
  data: z.record(z.string(), z.unknown()),
});
export type PullChange = z.infer<typeof pullChangeSchema>;

export const pullResponseSchema = z.object({
  changes: z.array(pullChangeSchema),
  nextCursor: z.string().nullish(),
  hasMore: z.boolean(),
  serverTime: z.iso.datetime({ offset: true }),
});
export type PullResponse = z.infer<typeof pullResponseSchema>;
