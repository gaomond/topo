import { type ReactNode, useEffect, useRef, useState } from "react";
import { licenseLinks } from "./licenseLinks";

export type LicenseNoticeBindings = {
  licenseOpen: boolean;
  onBlockingDialogChange: (open: boolean) => void;
};

export function LicenseNotice({
  children,
  entryVisible = true,
}: {
  children: (bindings: LicenseNoticeBindings) => ReactNode;
  entryVisible?: boolean;
}) {
  const [licenseOpen, setLicenseOpen] = useState(false);
  const [blockingDialogOpen, setBlockingDialogOpen] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);
  const entry = useRef<HTMLButtonElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const content = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (licenseOpen) {
      dialog.current?.showModal();
      closeButton.current?.focus();
    } else if (dialog.current?.open) {
      dialog.current.close();
      (entry.current ?? content.current)?.focus();
    }
  }, [licenseOpen]);

  return (
    <div className="flex h-dvh flex-col bg-slate-50 text-slate-900">
      <div
        ref={content}
        tabIndex={-1}
        className="relative min-h-0 flex-1 overflow-auto outline-none"
        inert={licenseOpen}
      >
        {children({ licenseOpen, onBlockingDialogChange: setBlockingDialogOpen })}
      </div>
      {entryVisible && (
        <footer
          className="shrink-0 border-t border-slate-200 bg-white px-3 py-1 text-center"
          inert={licenseOpen}
        >
          <button
            ref={entry}
            type="button"
            disabled={blockingDialogOpen}
            onClick={() => setLicenseOpen(true)}
            className="min-h-11 rounded px-3 text-sm underline underline-offset-4 focus-visible:outline-2 disabled:text-slate-400"
          >
            ライセンス・データ出典
          </button>
        </footer>
      )}
      <dialog
        ref={dialog}
        aria-labelledby="license-notice-title"
        onKeyDown={(event) => {
          if (event.key !== "Tab") return;
          // 標準dialogでも端からブラウザUIへ抜けるため、文書内の移動を循環させる。
          const controls = event.currentTarget.querySelectorAll<HTMLElement>("button, a[href]");
          const first = controls[0];
          const last = controls[controls.length - 1];
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last?.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first?.focus();
          }
        }}
        onCancel={(event) => {
          event.preventDefault();
          setLicenseOpen(false);
        }}
        className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-xl overflow-y-auto rounded-xl bg-white p-5 text-base leading-relaxed shadow-xl backdrop:bg-black/40"
      >
        <div className="mb-4 flex items-start justify-between gap-3">
          <h2 id="license-notice-title" className="text-xl font-bold">
            ライセンス・データ出典
          </h2>
          <button
            ref={closeButton}
            type="button"
            onClick={() => setLicenseOpen(false)}
            className="min-h-11 shrink-0 rounded border px-3"
          >
            閉じる
          </button>
        </div>
        <p className="mb-4 text-sm text-slate-600">詳細資料のリンクは別タブで開きます。</p>
        <ul className="list-disc space-y-4 pl-5 [overflow-wrap:anywhere]">
          <li>
            topoのコード・文書は MIT License です。
            <span className="block">Copyright (c) 2026 gaomond</span>
            <ExternalLink href={licenseLinks.license}>topoのLICENSE全文</ExternalLink>
          </li>
          <li>
            地図・神社データの出典：
            <ExternalLink href={licenseLinks.osm}>© OpenStreetMap contributors</ExternalLink>
            <span className="block">神社データには ODbL 1.0 が適用されます。</span>
            <ExternalLink href={licenseLinks.odbl}>ODbL 1.0本文</ExternalLink>
          </li>
          <li>
            <ExternalLink href={licenseLinks.data}>
              地図・神社データの出典・加工方法・利用条件
            </ExternalLink>
          </li>
          <li>
            地図画像の配信と、集計用の神社データの取得・投入は別の仕組みです。地図上にも出典を表示しています。
          </li>
          <li>依存ライブラリには、それぞれのライセンスが適用されます。</li>
        </ul>
      </dialog>
    </div>
  );
}

function ExternalLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="text-blue-800 underline underline-offset-4"
    >
      {children}
    </a>
  );
}
