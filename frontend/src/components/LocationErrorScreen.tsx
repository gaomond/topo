// エラー画面（Dumb）。
//
// 純表示部品。状態も Geolocation も持たない。初回許可拒否（PERMISSION_ERROR）と
// 非安全コンテキストの全面表示を描画する。文言＋（再試行可能なら）再試行ボタン。
// 再試行ロジック自体は Smart（useGeoTracking）が所有し、ここは onRetry を呼ぶだけ。

export type LocationErrorScreenProps = {
  message: string;
  // 再試行できる場合のみ渡す。未指定なら再試行ボタンを出さない（非安全コンテキスト等）。
  onRetry?: () => void;
};

export function LocationErrorScreen({ message, onRetry }: LocationErrorScreenProps) {
  return (
    <div
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="location-error-title"
      className="absolute inset-0 z-[2000] flex items-center justify-center bg-neutral-50 p-4"
    >
      <div className="max-w-sm text-center">
        <h1 id="location-error-title" className="mb-3 text-xl font-semibold text-neutral-800">
          位置情報を取得できません
        </h1>
        <p className="mb-5 text-neutral-600">{message}</p>
        {onRetry !== undefined && (
          <button
            type="button"
            onClick={onRetry}
            className="cursor-pointer rounded-md bg-blue-600 px-6 py-2.5 text-base text-white hover:bg-blue-700"
          >
            再試行
          </button>
        )}
      </div>
    </div>
  );
}
