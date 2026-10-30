/** Summarize the retained samples; unavailable values never contribute zero. */
export function summarizeHistory(buffer) {
  const values = buffer.filter(Number.isFinite);
  return {
    current: Number.isFinite(buffer.at(-1)) ? buffer.at(-1) : null,
    min: values.length ? Math.min(...values) : null,
    max: values.length ? Math.max(...values) : null
  };
}
