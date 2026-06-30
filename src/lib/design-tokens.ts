// Design System Tokens
// Created with accessibility, consistency, and scalability in mind

export const DesignTokens = {
  // Colors - semantic naming for better maintainability
  colors: {
    // Primary palette from existing theme
    primary: {
      50: '#E6F0FF',
      100: '#CCE1FF',
      200: '#99C2FF',
      300: '#66A3FF',
      400: '#3385FF',
      500: '#3366CC', // Main primary
      600: '#2952A3',
      700: '#1E3D7A',
      800: '#142452',
      900: '#0A1029',
    },
    // Secondary colors for support elements
    secondary: {
      50: '#FFF0E6',
      100: '#FFE0CC',
      200: '#FFC299',
      300: '#FFA266',
      400: '#FF8033',
      500: '#E56B00',
      600: '#B45500',
      700: '#854100',
      800: '#653C00',
      900: '#462700',
    },
    // Surface colors with cream background (existing)
    background: {
      cream: '#FEFCF5', // Main background (existing)
      white: '#FFFFFF',
      gray: {
        50: '#F9FAFB',
        100: '#F3F4F6',
        200: '#E5E7EB',
        300: '#D1D5DB',
        400: '#9CA3AF',
        500: '#6B7280',
        600: '#4B5563',
        700: '#374151',
        800: '#1F2937',
        900: '#111827',
      },
    },
    // Status colors for feedback
    status: {
      success: {
        value: '#10B981',
        light: '#D1FAE5',
        dark: '#065F46',
      },
      warning: {
        value: '#F59E0B',
        light: '#FEF3C7',
        dark: '#92400E',
      },
      error: {
        value: '#EF4444',
        light: '#FEE2E2',
        dark: '#991B1B',
      },
      info: {
        value: '#3B82F6',
        light: '#DBEAFE',
        dark: '#1E40AF',
      },
    },
    // Text colors for accessibility
    text: {
      primary: '#1A1A2E', // Dark (existing)
      secondary: '#4B5563',
      tertiary: '#9CA3AF',
      disabled: '#D1D5DB',
      white: '#FFFFFF',
    },
    // Border colors
    border: {
      default: '#E5E7EB',
      focus: '#3366CC',
      error: '#EF4444',
      success: '#10B981',
      dark: '#1A1A2E',
    },
    // Overlay colors for modals, dropdowns, etc.
    overlay: {
      dark: 'rgba(0, 0, 0, 0.6)',
      medium: 'rgba(0, 0, 0, 0.4)',
      light: 'rgba(0, 0, 0, 0.2)',
    },
  },

  // Typography scale based on current site hierarchy
  typography: {
    fontFamily: {
      sans: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
      mono: 'SFMono-Regular, Consolas, "Liberation Mono", Menlo, monospace',
    },
    fontSize: {
      xs: '0.75rem',     // 12px
      sm: '0.875rem',   // 14px
      base: '1rem',     // 16px
      lg: '1.125rem',   // 18px
      xl: '1.25rem',    // 20px
      '2xl': '1.5rem',  // 24px
      '3xl': '1.875rem', // 30px
      '4xl': '2.25rem', // 36px
      '5xl': '3rem',    // 48px
    },
    fontWeight: {
      normal: '400',
      medium: '500',
      semibold: '600',
      bold: '700',
    },
    lineHeight: {
      tight: '1.25',
      normal: '1.5',
      relaxed: '1.75',
      loose: '2',
    },
    letterSpacing: {
      tighter: '-0.05em',
      tight: '-0.025em',
      normal: '0',
      wide: '0.025em',
      wider: '0.05em',
    },
  },

  // Spacing scale based on 4px grid (consistent with Tailwind)
  spacing: {
    px: '1px',
    0: '0',
    0.5: '0.125rem',   // 2px
    1: '0.25rem',      // 4px
    1.5: '0.375rem',   // 6px
    2: '0.5rem',       // 8px
    2.5: '0.625rem',   // 10px
    3: '0.75rem',      // 12px
    3.5: '0.875rem',   // 14px
    4: '1rem',         // 16px
    5: '1.25rem',      // 20px
    6: '1.5rem',       // 24px
    7: '1.75rem',      // 28px
    8: '2rem',         // 32px
    10: '2.5rem',      // 40px
    12: '3rem',        // 48px
    16: '4rem',        // 64px
    20: '5rem',        // 80px
    24: '6rem',        // 96px
    32: '8rem',        // 128px
  },

  // Border radius - maintaining existing rounded-md (8px) and adding others
  borderRadius: {
    none: '0',
    sm: '0.125rem',    // 2px
    md: '0.375rem',    // 6px (existing rounded-md)
    lg: '0.5rem',      // 8px
    xl: '0.75rem',     // 12px
    '2xl': '1rem',     // 16px
    '3xl': '1.5rem',   // 24px
    full: '9999px',
  },

  // Shadows - following modern design patterns
  shadows: {
    sm: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
    md: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
    lg: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
    '2xl': '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
    inner: 'inset 0 2px 4px 0 rgba(0, 0, 0, 0.06)',
    none: 'none',
  },

  // Z-index scale
  zIndex: {
    hide: -1,
    auto: 'auto',
    base: '0',
    docked: '10',
    dropdown: '50',
    sticky: '100',
    banner: '200',
    overlay: '300',
    modal: '400',
    popover: '500',
    toast: '600',
    skipNav: '700',
    tooltip: '800',
  },

  // Animation durations
  animation: {
    fastest: '0.15s',
    fast: '0.3s',
    normal: '0.5s',
    slow: '0.8s',
    slower: '1.2s',
  },

  // Easing functions
  easing: {
    linear: 'linear',
    in: 'cubic-bezier(0.4, 0, 1, 1)',
    out: 'cubic-bezier(0, 0, 0.2, 1)',
    inOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
  },
};

export type DesignTokens = typeof DesignTokens;
export type ColorScale = typeof DesignTokens.colors.primary;
export type TypographyScale = typeof DesignTokens.typography;
export type SpacingScale = typeof DesignTokens.spacing;
