import logging
logging.disable(logging.CRITICAL)
import sys
from androguard.core.apk import APK
from androguard.core.dex import DEX

apk = APK('/home/ian/Downloads/BenroConnect_1727595281455.apk')
all_dex_names = list(apk.get_dex_names())
print(f'All dex names: {all_dex_names}', file=sys.stderr)
# get_dex() returns bytes for classes.dex; for others we need to read the file
import zipfile
z = zipfile.ZipFile('/home/ian/Downloads/BenroConnect_1727595281455.apk')
for name in all_dex_names:
    print(f'=== {name} ===', file=sys.stderr)
    if name == 'classes.dex':
        data = apk.get_dex()
    else:
        data = z.read(name)
    dex = DEX(data)
    n_classes = 0
    for cls in dex.get_classes():
        n_classes += 1
        cname = cls.get_name()
        if 'polaris' in cname.lower() and 'PolarisCMD' in cname:
            print(f'  Class: {cname}')
            for field in cls.get_fields():
                fname = field.get_name()
                if fname.startswith('SP_'):
                    try:
                        init_val = field.get_init_value()
                        val = init_val.get_value() if init_val else None
                    except Exception:
                        val = '?'
                    print(f'    {fname} = {val}')
    print(f'  ({n_classes} classes in {name})', file=sys.stderr)
