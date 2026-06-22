import { describe, it, expect } from 'vitest';
import { COLORS } from './palette';

describe('Palette', () => {
  it('primary should be #5B8DEE', () => {
    expect(COLORS.primary).toBe('#5B8DEE');
  });

  it('cream should be #FEFCF5', () => {
    expect(COLORS.cream).toBe('#FEFCF5');
  });

  it('dark should be #1A1A2E', () => {
    expect(COLORS.dark).toBe('#1A1A2E');
  });

  it('all colors should be valid 6-digit hex', () => {
    const hexRegex = /^#[0-9A-Fa-f]{6}$/;
    Object.values(COLORS).forEach((color) => {
      expect(color).toMatch(hexRegex);
    });
  });
});
