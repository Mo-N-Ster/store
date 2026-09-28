export interface SubTab {
  id: string;
  label: string;
  count?: number;
}

export function SubTabs({
  tabs,
  active,
  onChange,
  ariaLabel,
}: {
  tabs: SubTab[];
  active: string;
  onChange: (id: string) => void;
  ariaLabel: string;
}) {
  const move = (current: number, direction: number) => {
    const next = (current + direction + tabs.length) % tabs.length;
    onChange(tabs[next].id);
    document.getElementById(`subtab-${tabs[next].id}`)?.focus();
  };
  return (
    <div className="sub-tabs" role="tablist" aria-label={ariaLabel}>
      {tabs.map((tab, index) => (
        <button
          id={`subtab-${tab.id}`}
          type="button"
          role="tab"
          aria-selected={active === tab.id}
          tabIndex={active === tab.id ? 0 : -1}
          className={active === tab.id ? 'active' : ''}
          onClick={() => onChange(tab.id)}
          onKeyDown={(event) => {
            if (event.key === 'ArrowRight') {
              event.preventDefault();
              move(index, 1);
            } else if (event.key === 'ArrowLeft') {
              event.preventDefault();
              move(index, -1);
            } else if (event.key === 'Home') {
              event.preventDefault();
              move(0, 0);
            } else if (event.key === 'End') {
              event.preventDefault();
              move(tabs.length - 1, 0);
            }
          }}
          key={tab.id}
        >
          {tab.label}
          {tab.count !== undefined && <span>{tab.count}</span>}
        </button>
      ))}
    </div>
  );
}
