import { formatArea } from "@/shared/formatArea";
import purposeImage from "./assets/game-purpose-little-planet-v3.png";

export function GamePurposeView({ areaThreshold }: { areaThreshold: number }) {
  return (
    <section
      aria-labelledby="game-purpose-title"
      className="rounded-2xl bg-white p-4 shadow-sm sm:p-6"
    >
      <h2 id="game-purpose-title" className="text-lg font-bold text-slate-800">
        仲間と歩いて、街を囲もう
      </h2>
      <ul className="mt-2 list-disc space-y-2 pl-5 text-sm leading-relaxed text-slate-700">
        <li>
          {formatArea(areaThreshold)}以内で、できるだけ多くの地物（建物や施設など）を囲みましょう。
        </li>
        <li>全員が位置を確定すると、囲んだ範囲にある対象の地物の数が分かります。</li>
      </ul>
      <figure className="mx-auto mt-3 max-w-sm">
        <img
          src={purposeImage}
          width={1536}
          height={1024}
          alt="3人の位置を結んだ三角形で地物を囲む模式図"
          className="h-auto w-full"
        />
        <figcaption className="text-center text-xs leading-relaxed text-slate-600">
          <span className="block font-medium">3人で囲む例</span>
          丸はプレイヤーの位置、四角は地物。囲んだ範囲の内側の地物を数えます。
        </figcaption>
      </figure>
    </section>
  );
}
