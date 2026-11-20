import { z } from "zod";
import { UNITS } from "@/lib/domain/quantity";

/**
 * Contratos dos dados recebidos do servidor. Tudo que chega pela rede é
 * validado em tempo de execução antes de ser gravado no IndexedDB.
 */

const uuid = z.uuid();
const decimal = z.string().regex(/^-?\d+\.\d{3}$/, "decimal com 3 casas");
const isoDateTime = z.iso.datetime({ offset: true });
const isoDate = z.iso.date();
const optionalText = z.string().nullish();

export const unitSchema = z.enum(UNITS);

export const PERMISSIONS = [
  "ORGANIZATION_MANAGE",
  "COMMUNITY_MANAGE",
  "USER_MANAGE",
  "DEVICE_MANAGE",
  "COMMUNITY_READ",
  "FAMILY_READ",
  "FAMILY_WRITE",
  "DONOR_MANAGE",
  "FOOD_MANAGE",
  "DONATION_WRITE",
  "STOCK_READ",
  "STOCK_WRITE",
  "STOCK_ADJUST",
  "DISTRIBUTION_WRITE",
  "LOSS_WRITE",
  "MONITORING_WRITE",
  "ALERT_RESOLVE",
  "REPORT_READ",
  "SYNC_CONFLICT_RESOLVE",
] as const;
export const permissionSchema = z.enum(PERMISSIONS);
export type Permission = z.infer<typeof permissionSchema>;

export const communitySchema = z.object({
  id: uuid,
  organizationId: uuid,
  name: z.string(),
  description: optionalText,
  municipality: optionalText,
  approximateLocation: optionalText,
  primaryDeviceId: uuid.nullish(),
  active: z.boolean(),
});
export type Community = z.infer<typeof communitySchema>;

export const FOOD_CATEGORIES = [
  "GRAINS_AND_CEREALS",
  "BEANS_AND_LEGUMES",
  "FLOURS",
  "CANNED_GOODS",
  "DAIRY",
  "MEAT_AND_FISH",
  "FRUITS_AND_VEGETABLES",
  "OILS_AND_FATS",
  "SUGARS_AND_SWEETS",
  "BEVERAGES",
  "INFANT_FOOD",
  "OTHER",
] as const;
export const foodCategorySchema = z.enum(FOOD_CATEGORIES);
export type FoodCategory = z.infer<typeof foodCategorySchema>;

export const foodSchema = z.object({
  id: uuid,
  name: z.string(),
  category: foodCategorySchema,
  unit: unitSchema,
  description: optionalText,
  storageGuidance: optionalText,
  expirationWarningDays: z.number().int(),
  active: z.boolean(),
});
export type Food = z.infer<typeof foodSchema>;

export const donorSchema = z.object({
  id: uuid,
  type: z.enum(["INDIVIDUAL", "ORGANIZATION"]),
  name: z.string(),
  notes: optionalText,
  active: z.boolean(),
});
export type Donor = z.infer<typeof donorSchema>;

export const storageLocationSchema = z.object({
  id: uuid,
  communityId: uuid,
  name: z.string(),
  description: optionalText,
  active: z.boolean(),
});
export type StorageLocation = z.infer<typeof storageLocationSchema>;

export const familySchema = z.object({
  id: uuid,
  communityId: uuid,
  code: z.string(),
  memberCount: z.number().int(),
  status: z.enum(["ACTIVE", "INACTIVE"]),
  notes: optionalText,
});
export type Family = z.infer<typeof familySchema>;

export const EXPIRATION_STATUSES = ["KNOWN", "NOT_APPLICABLE", "UNKNOWN"] as const;
export const expirationStatusSchema = z.enum(EXPIRATION_STATUSES);
export type ExpirationStatus = z.infer<typeof expirationStatusSchema>;

export const BATCH_STATUSES = ["AVAILABLE", "BLOCKED", "DEPLETED"] as const;

export const batchSchema = z.object({
  id: uuid,
  communityId: uuid,
  foodId: uuid,
  storageLocationId: uuid.nullish(),
  donationId: uuid.nullish(),
  donationItemId: uuid.nullish(),
  originDescription: optionalText,
  initialQuantity: decimal,
  availableQuantity: decimal,
  unit: unitSchema,
  receivedAt: isoDateTime,
  expirationStatus: expirationStatusSchema,
  expirationDate: isoDate.nullish(),
  notes: optionalText,
  status: z.enum(BATCH_STATUSES),
});
export type Batch = z.infer<typeof batchSchema>;

export const MOVEMENT_TYPES = ["ENTRY", "DISTRIBUTION", "LOSS", "ADJUSTMENT"] as const;

export const movementSchema = z.object({
  id: uuid,
  communityId: uuid,
  batchId: uuid,
  foodId: uuid,
  type: z.enum(MOVEMENT_TYPES),
  quantityDelta: decimal,
  balanceAfter: decimal,
  sourceType: z.string(),
  sourceId: uuid.nullish(),
  justification: optionalText,
  performedBy: uuid,
  occurredAt: isoDateTime,
});
export type Movement = z.infer<typeof movementSchema>;

export const donationSchema = z.object({
  id: uuid,
  communityId: uuid,
  donorId: uuid.nullish(),
  originDescription: optionalText,
  receivedAt: isoDateTime,
  notes: optionalText,
  registeredBy: uuid,
  items: z.array(
    z.object({
      id: uuid,
      foodId: uuid,
      quantity: decimal,
      unit: unitSchema,
      notes: optionalText,
    }),
  ),
});
export type Donation = z.infer<typeof donationSchema>;

export const distributionSchema = z.object({
  id: uuid,
  communityId: uuid,
  distributedAt: isoDateTime,
  notes: optionalText,
  performedBy: uuid,
  familyIds: z.array(uuid),
  items: z.array(
    z.object({ id: uuid, batchId: uuid, foodId: uuid, quantity: decimal, unit: unitSchema }),
  ),
});
export type Distribution = z.infer<typeof distributionSchema>;

export const LOSS_REASONS = [
  "HUMIDITY",
  "HEAT_OR_SUN_EXPOSURE",
  "DAMAGED_PACKAGING",
  "EXPIRATION",
  "PESTS",
  "TRANSPORT",
  "SUSPECTED_CONTAMINATION",
  "OTHER",
  "UNIDENTIFIED",
] as const;
export const lossReasonSchema = z.enum(LOSS_REASONS);
export type LossReason = z.infer<typeof lossReasonSchema>;

export const lossSchema = z.object({
  id: uuid,
  communityId: uuid,
  batchId: uuid,
  foodId: uuid,
  quantity: decimal,
  unit: unitSchema,
  occurredAt: isoDateTime,
  reason: lossReasonSchema,
  description: optionalText,
  status: z.enum(["REGISTERED", "UNDER_REVIEW", "CLOSED"]),
  recordedBy: uuid,
});
export type Loss = z.infer<typeof lossSchema>;

export const readingSchema = z.object({
  id: uuid,
  communityId: uuid,
  storageLocationId: uuid,
  temperature: z.string().nullish(),
  humidity: z.string().nullish(),
  source: z.enum(["MANUAL", "SENSOR"]),
  inspection: z.boolean(),
  notes: optionalText,
  recordedAt: isoDateTime,
  recordedBy: uuid,
});
export type Reading = z.infer<typeof readingSchema>;

export const ALERT_TYPES = [
  "EXPIRING_SOON",
  "LOW_STOCK",
  "LOSS_INCREASE",
  "READING_OUT_OF_RANGE",
  "INSPECTION_OVERDUE",
] as const;

export const alertSchema = z.object({
  id: uuid,
  communityId: uuid,
  type: z.enum(ALERT_TYPES),
  severity: z.enum(["INFO", "WARNING", "CRITICAL"]),
  status: z.enum(["OPEN", "RESOLVED"]),
  subjectType: z.string(),
  subjectId: uuid.nullish(),
  message: z.string(),
  createdAt: isoDateTime,
  resolvedAt: isoDateTime.nullish(),
  resolutionNotes: optionalText,
});
export type Alert = z.infer<typeof alertSchema>;

/** Tipos de entidade do feed de sincronização e seus contratos. */
export const SYNC_ENTITY_SCHEMAS = {
  COMMUNITY: communitySchema,
  FOOD: foodSchema,
  DONOR: donorSchema,
  STORAGE_LOCATION: storageLocationSchema,
  FAMILY: familySchema,
  DONATION: donationSchema,
  BATCH: batchSchema,
  MOVEMENT: movementSchema,
  DISTRIBUTION: distributionSchema,
  LOSS: lossSchema,
  READING: readingSchema,
  ALERT: alertSchema,
} as const;
export type SyncEntityType = keyof typeof SYNC_ENTITY_SCHEMAS;
