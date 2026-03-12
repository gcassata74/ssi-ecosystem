import { defineConfig } from 'tsup';

type Format = 'cjs' | 'esm';

export default defineConfig(({ watch }) => ({
  entry: {
    index: 'src/index.ts',
    'angular/index': 'src/angular/index.ts'
  },
  format: ['esm', 'cjs'] satisfies Format[],
  sourcemap: true,
  clean: !watch,
  dts: true,
  splitting: false,
  shims: false,
  minify: false,
  treeshake: true,
  target: 'es2020'
}));
