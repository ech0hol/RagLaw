import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { StreamingEllipsis } from './StreamingEllipsis';

afterEach(() => {
  cleanup();
});

describe('StreamingEllipsis', () => {
  it('does not render when visible is false', () => {
    render(<StreamingEllipsis visible={false} />);
    expect(screen.queryByTestId('chat-streaming-ellipsis')).toBeNull();
  });

  it('renders animated dots when visible', () => {
    render(<StreamingEllipsis visible />);
    const el = screen.getByTestId('chat-streaming-ellipsis');
    expect(el.className).toContain('rl-chat-ellipsis');
    expect(el.querySelector('.rl-chat-ellipsis__dots')).not.toBeNull();
  });
});
