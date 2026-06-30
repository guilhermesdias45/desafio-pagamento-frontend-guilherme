export function createMockJwt(claims: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify(claims));
  const signature = btoa('fake-signature');
  return `${header}.${payload}.${signature}`;
}
