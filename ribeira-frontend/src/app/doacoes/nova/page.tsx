"use client";

import { useRouter } from "next/navigation";
import { useLiveQuery } from "dexie-react-hooks";
import { Notice, PageHeader } from "@/components/ui/Feedback";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import { useDonors, useStorageLocations } from "@/features/data/hooks";
import { DonationForm } from "@/features/donations/DonationForm";
import { recordOperation } from "@/features/operations/record";

export default function NewDonationPage() {
  return (
    <Protected>
      <NewDonation />
    </Protected>
  );
}

function NewDonation() {
  const { db, session, communityId, can, requestSync } = useApp();
  const router = useRouter();
  const foods = useLiveQuery(() => db.foods.orderBy("name").toArray(), [db]) ?? [];
  const donors = useDonors();
  const locations = useStorageLocations();

  if (!can("DONATION_WRITE")) {
    return (
      <Notice tone="warning" title="Sem permissão">
        Seu perfil não registra doações nesta comunidade.
      </Notice>
    );
  }
  return (
    <>
      <PageHeader
        title="Registrar doação"
        description="Fica salva no aparelho e é enviada quando houver conexão."
      />
      {foods.length === 0 ? (
        <Notice tone="warning">
          O catálogo de alimentos ainda não foi baixado. Conecte-se para sincronizar.
        </Notice>
      ) : null}
      <DonationForm
        foods={foods}
        donors={donors}
        locations={locations}
        onSubmit={async (payload) => {
          if (!session || !communityId) return;
          await recordOperation(db, session, communityId, "DONATION_RECEIVE", payload);
          void requestSync();
          router.push("/doacoes/?salvo=1");
        }}
      />
    </>
  );
}
