import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Food } from "@/lib/api/schemas";
import { DonationForm } from "./DonationForm";

const rice: Food = {
  id: "3b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f",
  name: "Arroz",
  category: "GRAINS_AND_CEREALS",
  unit: "KILOGRAM",
  expirationWarningDays: 7,
  active: true,
};
const cans: Food = {
  ...rice,
  id: "4b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f",
  name: "Sardinha",
  unit: "UNIT",
};

describe("DonationForm", () => {
  it("shows accessible errors and does not submit an incomplete donation", async () => {
    const onSubmit = vi.fn();
    render(<DonationForm foods={[rice]} donors={[]} locations={[]} onSubmit={onSubmit} />);

    await userEvent.click(screen.getByRole("button", { name: "Salvar doação" }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Origem da doação")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText("Selecione o alimento.")).toBeInTheDocument();
  });

  it("rejects fractions for countable units", async () => {
    const onSubmit = vi.fn();
    render(<DonationForm foods={[cans]} donors={[]} locations={[]} onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText("Origem da doação"), "Campanha fictícia");
    await userEvent.selectOptions(screen.getByLabelText(/Alimento/), cans.id);
    await userEvent.type(screen.getByLabelText(/Quantidade/), "2,5");
    await userEvent.selectOptions(screen.getByLabelText("Validade"), "UNKNOWN");
    await userEvent.click(screen.getByRole("button", { name: "Salvar doação" }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByText(/número inteiro/)).toBeInTheDocument();
  });

  it("builds a payload with device-generated IDs and decimal strings", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<DonationForm foods={[rice]} donors={[]} locations={[]} onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText("Origem da doação"), "Campanha fictícia");
    await userEvent.selectOptions(screen.getByLabelText(/Alimento/), rice.id);
    await userEvent.type(screen.getByLabelText(/Quantidade/), "12,5");
    await userEvent.type(screen.getByLabelText(/Data de validade/), "2027-01-31");
    await userEvent.click(screen.getByRole("button", { name: "Salvar doação" }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    const payload = onSubmit.mock.calls[0]![0];
    expect(payload.originDescription).toBe("Campanha fictícia");
    expect(payload.items[0].quantity).toBe("12.500");
    expect(payload.items[0].batches[0]).toMatchObject({
      quantity: "12.500",
      expirationStatus: "KNOWN",
      expirationDate: "2027-01-31",
    });
    expect(payload.donationId).toMatch(/^[0-9a-f-]{36}$/);
  });
});
