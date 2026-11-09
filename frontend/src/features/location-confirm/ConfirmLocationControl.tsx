import { useEffect, useRef } from "react";

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
      <button
        type="button"
        disabled={!canConfirm}
        onClick={onOpen}
        className="w-full rounded-lg bg-blue-700 px-6 py-3 font-bold text-white shadow-lg disabled:bg-slate-500"
      >
        {confirmed ? "確定済み" : submitting ? "確定中…" : "この位置で確定"}
      </button>
      <dialog
        ref={dialog}
        aria-labelledby="confirm-location-title"
        onCancel={(event) => {
          event.preventDefault();
          onCancel();
        }}
        className="m-auto max-w-sm rounded-xl p-6 shadow-xl backdrop:bg-black/40"
      >
        <p id="confirm-location-title" className="mb-6">
          この位置で確定しますか？ 確定すると変更できません。
        </p>
        <div className="flex justify-end gap-4">
          <button
            type="button"
            onClick={onCancel}
            disabled={submitting}
            className="rounded border px-4 py-2"
          >
            キャンセル
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={!canConfirm}
            className="rounded bg-blue-700 px-4 py-2 text-white disabled:bg-slate-500"
          >
            {submitting ? "確定中…" : "確定する"}
          </button>
        </div>
      </dialog>
    </div>
  );
}
