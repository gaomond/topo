import { type ReactNode, useEffect, useRef, useState } from "react";
import { Button } from "@/shared/ui/button";
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
    <div className="flex h-dvh flex-col bg-background text-foreground">
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
          className="shrink-0 bg-background px-5 py-1 text-right sm:px-10"
          inert={licenseOpen}
        >
          <Button
            ref={entry}
            type="button"
            disabled={blockingDialogOpen}
            onClick={() => setLicenseOpen(true)}
            variant="ghost"
            className="px-2 text-xs font-normal text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
          >
            licences
          </Button>
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
        className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-md overflow-y-auto rounded-3xl bg-surface p-6 text-sm leading-relaxed text-foreground shadow-xl backdrop:bg-black/40 sm:p-7"
      >
        <div className="mb-2 flex items-center justify-between gap-3">
          <h2 id="license-notice-title" className="text-xl font-semibold tracking-tight">
            Licences
          </h2>
          <Button
            ref={closeButton}
            type="button"
            onClick={() => setLicenseOpen(false)}
            variant="ghost"
            size="icon"
            aria-label="閉じる"
          >
            <X aria-hidden="true" className="size-5" />
          </Button>
        </div>
        <div className="divide-y divide-input [overflow-wrap:anywhere]">
          <section aria-labelledby="licences-topo" className="space-y-2 py-5">
            <h3 id="licences-topo" className="font-semibold">
              topo
            </h3>
            <p className="text-muted-foreground">
              © 2026 gaomond
              <br />
              All rights reserved
            </p>
            <NewTabLink href={licenseLinks.license}>権利表記</NewTabLink>
          </section>
          <section aria-labelledby="licences-map" className="space-y-2 py-5">
            <h3 id="licences-map" className="font-semibold">
              Map data
            </h3>
            <p className="text-xs text-muted-foreground">地図・神社データ</p>
            <NewTabLink href={licenseLinks.osm}>© OpenStreetMap contributors</NewTabLink>
            <div className="flex flex-wrap gap-x-4 gap-y-2">
              <NewTabLink href={licenseLinks.odbl}>ODbL 1.0</NewTabLink>
              <NewTabLink href={licenseLinks.data}>出典・加工方法</NewTabLink>
            </div>
          </section>
          <section aria-labelledby="licences-software" className="space-y-2 py-5">
            <h3 id="licences-software" className="font-semibold">
              Third-party software
            </h3>
            <p className="text-xs text-muted-foreground">
              第三者のコードには各ライセンスが適用されます。
            </p>
            <div className="flex flex-wrap gap-x-4 gap-y-2">
              <NewTabLink href={licenseLinks.dependencies}>使用ライブラリ</NewTabLink>
              <NewTabLink href={licenseLinks.uiComponents}>UIコンポーネント</NewTabLink>
            </div>
          </section>
        </div>
        <p className="text-xs text-muted-foreground">リンク先は別タブで開きます。</p>
      </dialog>
    </div>
  );
}

function NewTabLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-1 text-primary underline-offset-4 hover:underline"
    >
      {children}
      <ArrowUpRight aria-hidden="true" className="size-3 shrink-0" />
    </a>
  );
}

import { ArrowUpRight, X } from "lucide-react";
