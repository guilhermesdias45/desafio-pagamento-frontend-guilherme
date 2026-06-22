import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AuthLayout } from './AuthLayout';

describe('AuthLayout', () => {
  it('renders children inside the card', () => {
    render(<AuthLayout><div>Login Form</div></AuthLayout>);
    expect(screen.getByText('Login Form')).toBeInTheDocument();
  });

  it('applies cream background class', () => {
    const { container } = render(<AuthLayout><div>Content</div></AuthLayout>);
    const outerDiv = container.firstChild as HTMLElement;
    expect(outerDiv.className).toContain('bg-cream');
  });

  it('renders a centered card with white background', () => {
    render(<AuthLayout><div>Content</div></AuthLayout>);
    const card = document.querySelector('[class*="bg-white"]');
    expect(card).toBeInTheDocument();
  });

  it('card has shadow class', () => {
    render(<AuthLayout><div>Content</div></AuthLayout>);
    const card = document.querySelector('[class*="shadow"]');
    expect(card).toBeInTheDocument();
  });
});
