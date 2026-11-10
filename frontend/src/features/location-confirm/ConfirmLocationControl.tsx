import { useEffect, useRef } from "react";
import { Button } from "@/shared/ui/button";

type Props = {
  confirmed: boolean;
  dialogOpen: boolean;
  submitting: boolean;
  canConfirm: boolean;
  error: string | null;
  onOpen: () => void;
  onCancel: () => void;
  onConfirm: () => void;
};

export function ConfirmLocationControl({
  confirmed,
  dialogOpen,
  submitting,
  canConfirm,
  error,
  onOpen,
  onCancel,
  onConfirm,
}: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (dialogOpen) dialog.current?.showModal();
    else dialog.current?.close();
  }, [dialogOpen]);
  return (
    <div className="pointer-events-auto w-full max-w-sm self-center text-center">
      {error && (
        <p role="alert" className="mb-2 rounded bg-white p-3 text-red-700 shadow">
          {error}
        </p>
      )}
      <Button type="button" disabled={!canConfirm} onClick={onOpen} className="w-full shadow-lg">
        {confirmed ? "確定済み" : submitting ? "確定中…" : "この位置で確定"}
      </Button>
      <dialog
        ref={dialog}
        aria-labelledby="confirm-location-title"
        onCancel={(event) => {
          event.preventDefault();
          onCancel();
        }}
        className="m-auto w-[calc(100%-2rem)] max-w-sm rounded-3xl bg-surface p-6 text-foreground shadow-xl backdrop:bg-black/40"
      >
        <p id="confirm-location-title" className="mb-6">
          この位置で確定しますか？ 確定すると変更できません。
        </p>
        <div className="flex justify-end gap-4">
          <Button type="button" onClick={onCancel} disabled={submitting} variant="outline">
            キャンセル
          </Button>
          <Button type="button" onClick={onConfirm} disabled={!canConfirm}>
            {submitting ? "確定中…" : "確定する"}
          </Button>
        </div>
      </dialog>
    </div>
  );
}
