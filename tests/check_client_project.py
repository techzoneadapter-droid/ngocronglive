"""Static wiring checks; explicitly not a Unity compile or gameplay test."""
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'client-source'
settings = (root / 'ProjectSettings/EditorBuildSettings.asset').read_text()
scene = root / 'Assets/Scenes/SampleScene.unity'
assert 'Assets/Scenes/SampleScene.unity' in settings and scene.is_file()
guid = re.search(r'^guid: (\w+)', scene.with_suffix('.unity.meta').read_text(), re.M).group(1)
assert guid in settings
main_meta = (root / 'Assets/Scripts/Main.cs.meta').read_text()
main_guid = re.search(r'^guid: (\w+)', main_meta, re.M).group(1)
assert main_guid in scene.read_text()
main = (root / 'Assets/Scripts/Main.cs').read_text()
assert 'if (NroKeyboard.Draw()) return;' in main
assert 'base.useGUILayout = true;' in main
assert 'MyKeyMap.map(Event.current.keyCode)' not in main
assert 'if (!focused) NroKeyboard.ClearPressed();' in main
assert main.count('NroKeyboard.MapKey(') == 2
server_list = (root / 'Assets/Scripts/ServerListScreen.cs').read_text()
assert 'str = NroServerList.Normalize(str);' in server_list
assert 'NgocRongLive:127.0.0.1:14445:0,0,0' in server_list
print('PASS: scene GUID, Main attachment, keyboard hooks, IMGUI layout, focus release, bootstrap adapter')
