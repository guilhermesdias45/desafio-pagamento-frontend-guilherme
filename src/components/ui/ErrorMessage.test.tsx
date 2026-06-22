import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ErrorMessage } from './ErrorMessage';

describe('ErrorMessage', () => {
  it('renders title', () => {
    render(<ErrorMessage title="Erro" message="Algo deu errado" />);
    expect(screen.getByText('Erro')).toBeInTheDocument();
  });

  it('renders message', () => {
    render(<ErrorMessage title="Erro" message="Algo deu errado" />);
    expect(screen.getByText('Algo deu errado')).toBeInTheDocument();
  });

  it('renders retry button when onRetry is provided', () => {
    const onRetry = vi.fn();
    render(<ErrorMessage title="Erro" message="Algo deu errado" onRetry={onRetry} />);
    const retryBtn = screen.getByText('Tentar novamente');
    expect(retryBtn).toBeInTheDocument();
  });

  it('calls onRetry when retry button is clicked', () => {
    const onRetry = vi.fn();
    render(<ErrorMessage title="Erro" message="Algo deu errado" onRetry={onRetry} />);
    fireEvent.click(screen.getByText('Tentar novamente'));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('does not render retry button when onRetry is not provided', () => {
    render(<ErrorMessage title="Erro" message="Algo deu errado" />);
    expect(screen.queryByText('Tentar novamente')).not.toBeInTheDocument();
  });

  it('renders error icon', () => {
    render(<ErrorMessage title="Erro" message="Algo deu errado" />);
    const svg = document.querySelector('svg');
    expect(svg).toBeInTheDocument();
  });

  it('applies red-themed card classes', () => {
    render(<ErrorMessage title="Erro" message="Algo deu errado" />);
    const container = document.querySelector('[class*="bg-red"]') ?? document.querySelector('[class*="border-red"]');
    expect(container).toBeTruthy();
  });
});
