import type { GameObjectSnapshotPayload } from "@/api/types";

export type GameResultPresentation = {
  areaSqm: number;
  objectCount: number;
  objects: GameObjectSnapshotPayload[];
  polygon: [number, number][];
  vertices: [number, number][];
};

export function gameResultPresentation(value: unknown): GameResultPresentation | null {
  if (typeof value !== "object" || value === null) return null;
  if (
    !("areaValid" in value) ||
    value.areaValid !== true ||
    !("areaSqm" in value) ||
    typeof value.areaSqm !== "number" ||
    !Number.isFinite(value.areaSqm) ||
    value.areaSqm < 0 ||
    !("objectCount" in value) ||
    typeof value.objectCount !== "number" ||
    !Number.isSafeInteger(value.objectCount) ||
    value.objectCount < 0 ||
    !("polygon" in value) ||
    !Array.isArray(value.polygon) ||
    value.polygon.length < 4
  )
    return null;
  if (
    !("objects" in value) ||
    !Array.isArray(value.objects) ||
    value.objects.length !== value.objectCount
  )
    return null;
  const objects: GameObjectSnapshotPayload[] = [];
  const ids = new Set<string>();
  for (const object of value.objects) {
    if (
      typeof object !== "object" ||
      object === null ||
      typeof object.id !== "string" ||
      !/^-?(0|[1-9]\d*)$/.test(object.id) ||
      object.id.length > 20 ||
      object.objectType !== "shrine" ||
      !(object.name === null || typeof object.name === "string") ||
      typeof object.lat !== "number" ||
      typeof object.lng !== "number" ||
      !Number.isFinite(object.lat) ||
      !Number.isFinite(object.lng) ||
      Math.abs(object.lat) > 90 ||
      Math.abs(object.lng) > 180 ||
      ids.has(object.id)
    )
      return null;
    const id = BigInt(object.id);
    if (id < -9223372036854775808n || id > 9223372036854775807n || id.toString() !== object.id)
      return null;
    ids.add(object.id);
    objects.push({
      id: object.id,
      objectType: object.objectType,
      name: object.name,
      lat: object.lat,
      lng: object.lng,
    });
  }
  const polygon: [number, number][] = [];
  for (const point of value.polygon) {
    if (
      typeof point !== "object" ||
      point === null ||
      typeof point.lat !== "number" ||
      typeof point.lng !== "number" ||
      !Number.isFinite(point.lat) ||
      !Number.isFinite(point.lng) ||
      Math.abs(point.lat) > 90 ||
      Math.abs(point.lng) > 180
    )
      return null;
    polygon.push([point.lat, point.lng]);
  }
  const first = polygon[0];
  const last = polygon[polygon.length - 1];
  if (first[0] !== last[0] || first[1] !== last[1]) return null;
  const vertices = polygon.slice(0, -1);
  if (new Set(vertices.map(([lat, lng]) => `${lat},${lng}`)).size < 3) return null;
  return { areaSqm: value.areaSqm, objectCount: value.objectCount, objects, polygon, vertices };
}
