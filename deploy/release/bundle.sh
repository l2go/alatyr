#!/usr/bin/env bash
# Package the operator runbook, configuration examples and pinned compose stack.
set -euo pipefail
if (($# != 3)); then echo 'usage: bundle.sh VERSION IMAGE@DIGEST OUTDIR' >&2; exit 2; fi
version=$1 image=$2 out=$3
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'bundle.sh: invalid version' >&2; exit 2; }
[[ $image =~ ^ghcr\.io/l2go/alatyr(:[A-Za-z0-9._-]+)?@sha256:[a-f0-9]{64}$ ]] || { echo 'bundle.sh: expected a pinned ghcr.io/l2go/alatyr image' >&2; exit 2; }
deploy="$(cd "$(dirname "$0")/.." && pwd)"
python3 - "$deploy" "$version" "$image" "$out" <<'PY'
import gzip,hashlib,io,pathlib,sys,tarfile
root=pathlib.Path(sys.argv[1]);version,image=sys.argv[2:4];out=pathlib.Path(sys.argv[4]);out.mkdir(parents=True,exist_ok=True)
files={'LICENSE':root.parent/'LICENSE','README.md':root/'release/README.md','server.example.json':root/'server.example.json','character.example.json':root/'character.example.json'}
files.update((str(p.relative_to(root)),p) for p in sorted((root/'postgres/init').glob('*')) if p.is_file())
compose=(root/'compose.yaml').read_text();old='  image: ${ALATYR_IMAGE:-ghcr.io/l2go/alatyr:edge}'
if compose.count(old)!=1:raise SystemExit('bundle.sh: compose image declaration changed')
files['compose.yaml']=compose.replace(old,'  image: ${ALATYR_IMAGE:-'+image+'}').encode()
name='alatyr-'+version+'-compose.tar.gz';archive=out/name
with archive.open('wb') as stream, gzip.GzipFile(filename='',mode='wb',fileobj=stream,mtime=0) as compressed, tarfile.open(fileobj=compressed,mode='w',format=tarfile.USTAR_FORMAT) as tar:
 for relative,value in sorted(files.items()):
  data=value if isinstance(value,bytes) else value.read_bytes()
  info=tarfile.TarInfo('alatyr-'+version+'/'+relative);info.size=len(data);info.mode=0o755 if relative.endswith('.sh') else 0o644
  tar.addfile(info,io.BytesIO(data))
(out/(name+'.sha256')).write_text(hashlib.sha256(archive.read_bytes()).hexdigest()+'  '+name+'\n')
print(archive)
PY
