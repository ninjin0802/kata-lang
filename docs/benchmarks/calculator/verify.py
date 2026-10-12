import os
import sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "scripts"))
import e2e
e2e.SERIAL = "emulator-5554"
apk = sys.argv[1]
if len(sys.argv) > 2:
    e2e.ACTIVITY = sys.argv[2]
e2e.build = lambda example: apk
try:
    e2e.check_calculator()
    print("PASS")
except Exception as error:
    print("FAIL:", str(error)[:400])
    e2e.adb("uninstall", "dev.kata.calculator", check=False)
