import { useCallback, useLayoutEffect, useRef, useState } from "react";

export type InviteCopyStatus = "idle" | "copied" | "failed";

export function useInviteUrlCopy(
  inviteUrl: string,
  clipboard: Pick<Clipboard, "writeText"> | undefined,
) {
  const [feedback, setFeedback] = useState<{ url: string; status: InviteCopyStatus }>({
    url: inviteUrl,
    status: "idle",
  });
  const sequence = useRef(0);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useLayoutEffect(() => {
    // URLの切り替えと離脱で、旧コピーの応答と成功表示のタイマーを失効させる。
    sequence.current += 1;
    setFeedback({ url: inviteUrl, status: "idle" });
    return () => {
      sequence.current += 1;
      clearTimeout(timer.current);
    };
  }, [inviteUrl]);

  const copyInviteUrl = useCallback(async () => {
    const operation = ++sequence.current;
    clearTimeout(timer.current);
    setFeedback({ url: inviteUrl, status: "idle" });
    try {
      if (!clipboard) throw new Error("Clipboard unavailable");
      await clipboard.writeText(inviteUrl);
      if (sequence.current !== operation) return;
      setFeedback({ url: inviteUrl, status: "copied" });
      timer.current = setTimeout(() => {
        if (sequence.current === operation) setFeedback({ url: inviteUrl, status: "idle" });
      }, 2000);
    } catch {
      if (sequence.current === operation) setFeedback({ url: inviteUrl, status: "failed" });
    }
  }, [clipboard, inviteUrl]);

  return { status: feedback.url === inviteUrl ? feedback.status : "idle", copyInviteUrl };
}
