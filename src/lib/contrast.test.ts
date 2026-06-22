import { describe, it, expect } from 'vitest';
import { COLORS } from './palette';
import { hexToRgb, relativeLuminance, contrastRatio } from './contrast';

describe('contrast utilities', () => {
  describe('hexToRgb', () => {
    it('converts #5B8DEE correctly', () => {
      expect(hexToRgb('#5B8DEE')).toEqual([91, 141, 238]);
    });

    it('converts #FFFFFF correctly', () => {
      expect(hexToRgb('#FFFFFF')).toEqual([255, 255, 255]);
    });

    it('converts #000000 correctly', () => {
      expect(hexToRgb('#000000')).toEqual([0, 0, 0]);
    });
  });

  describe('relativeLuminance', () => {
    it('black is 0', () => {
      expect(relativeLuminance(0, 0, 0)).toBeCloseTo(0, 4);
    });

    it('white is 1', () => {
      expect(relativeLuminance(255, 255, 255)).toBeCloseTo(1, 4);
    });
  });

  describe('contrastRatio', () => {
    it('black on white is 21', () => {
      expect(contrastRatio('#000000', '#FFFFFF')).toBeCloseTo(21, 1);
    });

    it('white on black is 21', () => {
      expect(contrastRatio('#FFFFFF', '#000000')).toBeCloseTo(21, 1);
    });
  });

  describe('WCAG AA compliance', () => {
    it('primary bg + white text has contrast >= 4.5:1', () => {
      const ratio = contrastRatio(COLORS.primary, COLORS.white);
      expect(ratio).toBeGreaterThanOrEqual(4.5);
    });

    it('dark text on cream bg has contrast >= 4.5:1', () => {
      const ratio = contrastRatio(COLORS.dark, COLORS.cream);
      expect(ratio).toBeGreaterThanOrEqual(4.5);
    });
  });
});
