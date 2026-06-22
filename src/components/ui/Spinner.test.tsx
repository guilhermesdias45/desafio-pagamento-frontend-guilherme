import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Spinner } from './Spinner';

describe('Spinner', () => {
  it('renders an SVG element', () => {
    render(<Spinner />);
    const svg = document.querySelector('svg');
    expect(svg).toBeInTheDocument();
  });

  it('has animate-spin class', () => {
    render(<Spinner />);
    const svg = document.querySelector('svg');
    expect(svg).toHaveClass('animate-spin');
  });

  it('renders with sm size classes', () => {
    render(<Spinner size="sm" />);
    const svg = document.querySelector('svg');
    expect(svg).toHaveClass('h-4', 'w-4');
  });

  it('renders with md size classes', () => {
    render(<Spinner size="md" />);
    const svg = document.querySelector('svg');
    expect(svg).toHaveClass('h-6', 'w-6');
  });

  it('renders with lg size classes', () => {
    render(<Spinner size="lg" />);
    const svg = document.querySelector('svg');
    expect(svg).toHaveClass('h-8', 'w-8');
  });

  it('defaults to md size', () => {
    render(<Spinner />);
    const svg = document.querySelector('svg');
    expect(svg).toHaveClass('h-6', 'w-6');
  });
});
