from pathlib import Path
import subprocess,zipfile,os,secrets,hashlib
root=Path(__file__).parent
app=root/'app';build=root/'build';build.mkdir(exist_ok=True)
jdk=root/'toolchain/jdk-17.0.20.1+1';sdk=root/'toolchain/android-15';android=root/'toolchain/android-13/android.jar'
def run(args):
 print(str(args[0]).split('/')[-1],flush=True)
 subprocess.run([str(x) for x in args],check=True)
run([sdk/'aapt2','compile','--dir',app/'res','-o',build/'compiled.zip'])
(build/'generated').mkdir(exist_ok=True)
run([sdk/'aapt2','link','-o',build/'resources.apk','-I',android,'--manifest',app/'AndroidManifest.xml','--java',build/'generated','-A',app/'assets','--version-code','2','--version-name','2.0',build/'compiled.zip'])
(build/'classes').mkdir(exist_ok=True)
sources=sorted((app/'src').rglob('*.java'))+sorted((build/'generated').rglob('*.java'))
run([jdk/'bin/javac','--release','8','-encoding','UTF-8','-classpath',android,'-d',build/'classes',*sources])
(build/'dex').mkdir(exist_ok=True)
run([jdk/'bin/java','-cp',sdk/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',android,'--output',build/'dex',*sorted((build/'classes').rglob('*.class'))])
with zipfile.ZipFile(build/'resources.apk') as original,zipfile.ZipFile(build/'unsigned.apk','w') as target:
 for item in original.infolist():target.writestr(item,original.read(item.filename))
 target.write(build/'dex/classes.dex','classes.dex')
run([sdk/'zipalign','-f','4',build/'unsigned.apk',build/'aligned.apk'])
password=root/'signing-password.txt';keystore=root/'signing.keystore'
if not password.exists():password.write_text(secrets.token_urlsafe(32));password.chmod(0o600)
if not keystore.exists():
 run([jdk/'bin/keytool','-genkeypair','-keystore',keystore,'-storepass:file',password,'-keypass:file',password,'-alias','sumertime','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Sumerian Clock, O=Local Build'])
 keystore.chmod(0o600)
run([jdk/'bin/java','-jar',sdk/'lib/apksigner.jar','sign','--ks',keystore,'--ks-key-alias','sumertime','--ks-pass','file:'+str(password),'--out',root/'SumerianClock.apk',build/'aligned.apk'])
run([jdk/'bin/java','-jar',sdk/'lib/apksigner.jar','verify','--verbose',root/'SumerianClock.apk'])
sha=hashlib.sha256((root/'SumerianClock.apk').read_bytes()).hexdigest()
(root/'SumerianClock.apk.sha256').write_text(sha+'  SumerianClock.apk\n')
print('APK',sha,flush=True)
