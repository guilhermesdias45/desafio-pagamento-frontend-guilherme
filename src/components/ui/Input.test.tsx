import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Input } from './Input';

describe('Input', () => {
  it('renders a label', () => {
    render(<Input label="Email" />);
    expect(screen.getByText('Email')).toBeInTheDocument();
  });

  it('renders an input element', () => {
    render(<Input label="Email" />);
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('passes placeholder prop to the native input', () => {
    render(<Input label="Email" placeholder="Digite seu email" />);
    expect(screen.getByPlaceholderText('Digite seu email')).toBeInTheDocument();
  });

  it('displays error message when error prop is provided', () => {
    render(<Input label="Email" error="Campo obrigatório" />);
    expect(screen.getByText('Campo obrigatório')).toBeInTheDocument();
  });

  it('applies red text class to error message', () => {
    render(<Input label="Email" error="Campo obrigatório" />);
    const errorEl = screen.getByText('Campo obrigatório');
    expect(errorEl.className).toContain('text-red');
  });

  it('passes type prop to input', () => {
    render(<Input label="Senha" type="password" />);
    const input = screen.getByLabelText('Senha');
    expect(input).toHaveAttribute('type', 'password');
  });

  it('calls onChange when value changes', () => {
    const handleChange = vi.fn();
    render(<Input label="Nome" onChange={handleChange} />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'João' } });
    expect(handleChange).toHaveBeenCalled();
  });

  it('associates label with input via htmlFor', () => {
    render(<Input label="Email" id="email" />);
    const label = screen.getByText('Email');
    expect(label.tagName).toBe('LABEL');
  });
});
