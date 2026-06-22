import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AppLayout } from './AppLayout';

describe('AppLayout', () => {
  it('renders children inside main content area', () => {
    render(<AppLayout><div>Dashboard</div></AppLayout>);
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
  });

  it('renders a header with primary background', () => {
    render(<AppLayout><div>Content</div></AppLayout>);
    const header = document.querySelector('header');
    expect(header).toBeInTheDocument();
    expect(header!.className).toContain('bg-primary');
  });

  it('header displays app name', () => {
    render(<AppLayout><div>Content</div></AppLayout>);
    expect(screen.getByText('Acabou o Mony')).toBeInTheDocument();
  });

  it('renders a main element', () => {
    render(<AppLayout><div>Content</div></AppLayout>);
    const main = document.querySelector('main');
    expect(main).toBeInTheDocument();
  });

  it('header has white text', () => {
    render(<AppLayout><div>Content</div></AppLayout>);
    const header = document.querySelector('header');
    expect(header!.className).toContain('text-white');
  });
});
