"""Disposable HOST model, not Room/Android evidence. Synthetic data only."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import tempfile

BASE = Path(__file__).resolve().parent
POINTS = {
    1: 'before staging',
    2: 'during media staging (one of two files)',
    3: 'after staging before marker/replacement',
    4: 'after durable PREPARED marker',
    5: 'pointer temporary written, before atomic replace',
    6: 'pointer replaced, before new media verification',
    7: 'replacement verified, before cleanup',
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def durable(path, data):
    with path.open('wb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def atomic_json(path, value, interrupt=False):
    temp = path.with_suffix('.tmp')
    durable(temp, json.dumps(value, sort_keys=True).encode())
    if interrupt:
        os._exit(87)  # No exception unwinding or finally cleanup.
    os.replace(temp, path)


def make_generation(root, name, stop=None):
    generation = root / name
    generation.mkdir()
    media = generation / 'media'
    media.mkdir()
    db = sqlite3.connect(generation / 'data.sqlite')
    db.execute('PRAGMA foreign_keys=ON')
    assert db.execute('PRAGMA journal_mode=WAL').fetchone()[0] == 'wal'
    db.execute('PRAGMA synchronous=FULL')
    db.executescript('''
        PRAGMA user_version=1;
        CREATE TABLE state(id INTEGER PRIMARY KEY, generation TEXT NOT NULL);
        CREATE TABLE media(name TEXT PRIMARY KEY, hash TEXT NOT NULL,
            state_id INTEGER NOT NULL REFERENCES state(id));
    ''')
    db.execute('INSERT INTO state VALUES(1,?)', (name,))
    for index in (1, 2):
        payload = f'SYNTHETIC-{name}-{index}'.encode()
        db.execute('INSERT INTO media VALUES(?,?,1)', (f'{index}.bin', digest(payload)))
    db.commit()
    assert db.execute('PRAGMA wal_checkpoint(TRUNCATE)').fetchone() == (0, 0, 0)
    db.close()
    for index in (1, 2):
        durable(media / f'{index}.bin', f'SYNTHETIC-{name}-{index}'.encode())
        if index == 1 and stop == 2:
            os._exit(87)


def validate(root, name):
    if name not in ('old', 'new'):
        raise ValueError('unknown generation')
    generation = root / name
    with sqlite3.connect((generation / 'data.sqlite').as_uri() + '?mode=ro', uri=True) as db:
        assert db.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
        assert db.execute('PRAGMA foreign_key_check').fetchall() == []
        assert db.execute('PRAGMA user_version').fetchone() == (1,)
        assert db.execute('SELECT generation FROM state').fetchall() == [(name,)]
        rows = db.execute('SELECT name,hash FROM media ORDER BY name').fetchall()
        assert len(rows) == 2
        for filename, expected in rows:
            assert filename in ('1.bin', '2.bin')
            assert digest((generation / 'media' / filename).read_bytes()) == expected
    return name


def recover(root):
    active = json.loads((root / 'active.json').read_text())['generation']
    try:
        validate(root, active)
    except (AssertionError, OSError, sqlite3.Error, ValueError):
        journal = json.loads((root / 'journal.json').read_text())
        assert journal['operation'] == 'RESTORE' and journal['old'] == 'old'
        validate(root, 'old')
        active = 'old'
        atomic_json(root / 'active.json', {'generation': active})
    # An inactive partial generation is retained, never accepted as live data.
    # Preserve old recovery material; no recursive removal in the host model.
    atomic_json(root / 'result.json', {'active': active, 'verified': True})
    for filename in ('active.tmp', 'journal.tmp', 'result.tmp'):
        temporary = root / filename
        if temporary.is_file():
            temporary.unlink()
    return validate(root, active)


def interrupt_worker(root, point):
    if point == 1:
        os._exit(87)
    make_generation(root, 'new', point)
    validate(root, 'new')
    if point == 3:
        os._exit(87)
    # The old DB is already checkpointed and closed; never copy a live WAL DB.
    shutil.copyfile(root / 'old' / 'data.sqlite', root / 'safety.sqlite')
    atomic_json(root / 'journal.json', {'operation': 'RESTORE', 'state': 'PREPARED',
                                      'old': 'old', 'new': 'new'})
    if point == 4:
        os._exit(87)
    atomic_json(root / 'active.json', {'generation': 'new'}, point == 5)
    if point == 6:
        os._exit(87)
    validate(root, 'new')
    if point == 7:
        os._exit(87)
    raise AssertionError('unreached interruption')


def child(*args):
    return subprocess.run([sys.executable, str(Path(__file__).resolve()), *map(str, args)],
                          capture_output=True, text=True, timeout=30)


def run():
    output = BASE / 'out'
    output.mkdir(exist_ok=True)
    run_root = Path(tempfile.mkdtemp(prefix='synthetic-', dir=output)).resolve()
    results = []
    for point, label in POINTS.items():
        root = run_root / f'point-{point}'
        root.mkdir()
        make_generation(root, 'old')
        atomic_json(root / 'active.json', {'generation': 'old'})
        initial = (root / 'old' / 'data.sqlite').read_bytes()
        worker = child('interrupt', root, point)
        assert worker.returncode == 87, worker.stderr
        expected = 'old' if point <= 5 else 'new'
        recoveries = []
        for _ in range(3):
            recovery = child('recover', root)
            assert recovery.returncode == 0, recovery.stderr
            recoveries.append(recovery.stdout.strip())
        assert recoveries == [expected] * 3
        assert (root / 'old' / 'data.sqlite').read_bytes() == initial
        assert not list(root.glob('*.tmp'))
        results.append({'point': point, 'boundary': label, 'child_exit': 87,
                        'recoveries': recoveries, 'schema': 1,
                        'integrity': 'ok', 'foreign_keys': 'ok', 'media': 'consistent'})
    negatives = []
    for failure in ('corrupt-db', 'missing-media', 'altered-media'):
        root = run_root / failure
        root.mkdir()
        make_generation(root, 'old')
        make_generation(root, 'new')
        atomic_json(root / 'journal.json', {'operation': 'RESTORE', 'old': 'old', 'new': 'new'})
        atomic_json(root / 'active.json', {'generation': 'new'})
        if failure == 'corrupt-db':
            durable(root / 'new' / 'data.sqlite', b'NOT A DATABASE')
        elif failure == 'missing-media':
            (root / 'new' / 'media' / '2.bin').unlink()
        else:
            durable(root / 'new' / 'media' / '2.bin', b'ALTERED')
        recovery = child('recover', root)
        assert recovery.returncode == 0 and recovery.stdout.strip() == 'old', recovery.stderr
        negatives.append({'input': failure, 'recovery': 'old', 'invalid_new_accepted': False})
    evidence = {'scope': 'HOST PYTHON SQLITE MODEL ONLY', 'python': sys.version,
                'sqlite': sqlite3.sqlite_version, 'host_model': 'PASS',
                'SP-01': 'INCONCLUSIVE', 'real_android_process_death': False,
                'points': results, 'negative_cases': negatives,
                'limitations': ['Not Kotlin/Room/BundledSQLiteDriver/AtomicFile',
                                'Not power-loss or directory fsync proof',
                                'Not Android process death, WAL concurrency or SAF',
                                'RESTORE only; no production RESET implementation']}
    (output / 'results.json').write_text(json.dumps(evidence, indent=2), encoding='utf-8')
    print(json.dumps(evidence, indent=2))


if __name__ == '__main__':
    if len(sys.argv) == 1:
        run()
    else:
        target = Path(sys.argv[2]).resolve()
        assert target.is_relative_to(BASE / 'out') and target != BASE / 'out'
        if sys.argv[1] == 'interrupt':
            interrupt_worker(target, int(sys.argv[3]))
        elif sys.argv[1] == 'recover':
            print(recover(target))
        else:
            raise ValueError('unknown command')
