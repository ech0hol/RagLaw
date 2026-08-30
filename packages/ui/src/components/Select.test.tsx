import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Select } from './Select';

const options = [
  { value: 'civil', label: '民法商法' },
  { value: 'criminal', label: '刑法' },
];

afterEach(() => {
  cleanup();
});

describe('Select', () => {
  it('closes after selecting an option inside a label wrapper', () => {
    const onChange = vi.fn();
    render(
      <label>
        二级类目
        <Select value="" onChange={onChange} options={options} placeholder="请选择二级类目" />
      </label>,
    );

    const trigger = screen.getByRole('button', { name: '二级类目' });
    fireEvent.click(trigger);
    expect(trigger.getAttribute('aria-expanded')).toBe('true');

    fireEvent.click(screen.getByRole('option', { name: '刑法' }));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith('criminal');
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByRole('listbox')).toBeNull();
  });
});
