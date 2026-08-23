type SpinnerProps = {
  label?: string;
  centered?: boolean;
};

export function Spinner({ label = '加载中…', centered = false }: SpinnerProps) {
  const classes = ['rl-spinner', centered && 'rl-spinner--centered'].filter(Boolean).join(' ');
  return (
    <div className={classes}>
      <span className="rl-spinner__icon" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
