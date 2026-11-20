package com.ribeira.alert.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Starting points for the explicit alert rules; they must be reviewed with conservation experts and
 * the communities (docs/requirements.md, hypothesis in RF09).
 *
 * @param lossWindowDays size of the recent and of the previous comparison windows
 * @param lossIncreaseFactor recent losses must exceed previous ones by this factor
 * @param lossMinOccurrences minimum number of recent loss records before alerting
 */
@ConfigurationProperties("ribeira.alerts")
public record AlertProperties(
    int lossWindowDays, double lossIncreaseFactor, int lossMinOccurrences) {

  public AlertProperties {
    lossWindowDays = lossWindowDays <= 0 ? 30 : lossWindowDays;
    lossIncreaseFactor = lossIncreaseFactor <= 1 ? 2.0 : lossIncreaseFactor;
    lossMinOccurrences = lossMinOccurrences <= 0 ? 3 : lossMinOccurrences;
  }
}
