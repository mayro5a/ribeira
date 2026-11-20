package com.ribeira.report.api;

import com.ribeira.report.application.ReportService;
import com.ribeira.report.application.ReportService.CommunityDashboard;
import com.ribeira.report.application.ReportService.CommunityInventory;
import com.ribeira.report.application.ReportService.DistributionReport;
import com.ribeira.report.application.ReportService.DonationReport;
import com.ribeira.report.application.ReportService.LossReport;
import com.ribeira.report.application.ReportService.OrganizationDashboard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(
    name = "Relatórios e dashboards",
    description =
        "Somente agregados (REPORT_READ). Sem communityId, considera todas as comunidades"
            + " autorizadas. Período padrão: últimos 30 dias; máximo 366 dias.")
public class ReportController {

  private final ReportService service;

  public ReportController(ReportService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/reports/inventory")
  public List<CommunityInventory> inventory(@RequestParam(required = false) UUID communityId) {
    return service.inventory(communityId);
  }

  @GetMapping("/api/v1/reports/donations")
  public DonationReport donations(
      @RequestParam(required = false) UUID communityId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return service.donations(communityId, from, to);
  }

  @GetMapping("/api/v1/reports/distributions")
  public DistributionReport distributions(
      @RequestParam(required = false) UUID communityId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return service.distributions(communityId, from, to);
  }

  @GetMapping("/api/v1/reports/losses")
  public LossReport losses(
      @RequestParam(required = false) UUID communityId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return service.losses(communityId, from, to);
  }

  @GetMapping("/api/v1/dashboard/organization")
  @Operation(summary = "Indicadores consolidados das comunidades autorizadas")
  public OrganizationDashboard organization(
      @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
    return service.organizationDashboard(from, to);
  }

  @GetMapping("/api/v1/dashboard/communities/{communityId}")
  @Operation(summary = "Indicadores da comunidade (STOCK_READ)")
  public CommunityDashboard community(@PathVariable UUID communityId) {
    return service.communityDashboard(communityId);
  }
}
