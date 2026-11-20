package com.ribeira.donation.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DonationRepository extends JpaRepository<Donation, UUID> {

  /** First step of paging: IDs only, so items can be fetched without in-memory pagination. */
  @Query(
      """
      select d.id from Donation d
      where d.communityId = :communityId
        and d.receivedAt >= :from and d.receivedAt < :to
      """)
  Page<UUID> findIds(UUID communityId, Instant from, Instant to, Pageable pageable);

  @Query(
      "select distinct d from Donation d left join fetch d.items"
          + " where d.organizationId = :organizationId and d.id in :ids")
  List<Donation> findWithItems(UUID organizationId, Collection<UUID> ids);
}
