export function MovementChart({
  rows,
  entries,
  exits,
}: {
  rows: { period: string; entries: number; exits: number }[];
  entries: string;
  exits: string;
}) {
  const max = Math.max(1, ...rows.flatMap((row) => [row.entries, row.exits]));
  return (
    <div className="report-bar-chart" role="img" aria-label={`${entries} / ${exits}`}>
      <div className="report-legend">
        <span>
          <i className="legend-entry" />
          {entries}
        </span>
        <span>
          <i className="legend-exit" />
          {exits}
        </span>
      </div>
      <div className="report-bars">
        {rows.map((row) => (
          <div className="report-bar-group" key={row.period}>
            <div className="report-bar-pair">
              <i
                className="entry"
                style={{ height: `${(row.entries / max) * 100}%` }}
                title={`${entries}: ${row.entries}`}
              />
              <i
                className="exit"
                style={{ height: `${(row.exits / max) * 100}%` }}
                title={`${exits}: ${row.exits}`}
              />
            </div>
            <small>{row.period}</small>
          </div>
        ))}
      </div>
    </div>
  );
}
