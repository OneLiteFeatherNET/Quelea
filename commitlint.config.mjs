// Conventional Commits enforcement (commits + PR title).
// https://www.conventionalcommits.org
// release-please (release-please-config.json) parses commit types to decide version bumps and
// changelog sections, so this isn't just style - a non-conventional commit silently fails to
// bump the version or gets dropped from the changelog.
export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'header-max-length': [2, 'always', 100],
    'type-enum': [
      2,
      'always',
      ['build', 'chore', 'ci', 'docs', 'feat', 'fix', 'perf', 'refactor', 'revert', 'style', 'test'],
    ],
  },
};
