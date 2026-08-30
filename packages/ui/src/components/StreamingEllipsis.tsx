type StreamingEllipsisProps = {
  visible: boolean;
};

export function StreamingEllipsis({ visible }: StreamingEllipsisProps) {
  if (!visible) {
    return null;
  }

  return (
    <div className="rl-chat-ellipsis" data-testid="chat-streaming-ellipsis" aria-label="正在生成回答">
      <span className="rl-chat-ellipsis__dots" aria-hidden="true" />
    </div>
  );
}
