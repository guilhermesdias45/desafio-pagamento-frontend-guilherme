/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#3366CC',
        cream: '#FEFCF5',
        dark: '#1A1A2E',
      },
    },
  },
  plugins: [],
};
