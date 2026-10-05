"""Export a fresh film with a real actor and tone through the actual Record button."""
import array
import json
import math
import os
from pathlib import Path
import struct
import subprocess
import sys
import uuid
import wave
from smoke import call
from qa_environment import checked_client, report_path
from dashboard_smoke import dismiss_onboarding
import export_smoke


def main():
    target=checked_client()
    assets=Path(call('/bbs-export-fixture',{'paths':True})['assets'])
    name='sounds/__ai_export_'+uuid.uuid4().hex+'.wav'
    tone=assets/name
    tone.parent.mkdir(parents=True,exist_ok=True)
    rate=48000
    samples=[int(6000*math.sin(2*math.pi*440*i/rate)) for i in range(rate*2)]
    with wave.open(str(tone),'wb') as output:
        output.setnchannels(1);output.setsampwidth(2);output.setframerate(rate)
        output.writeframes(struct.pack('<'+'h'*len(samples),*samples))
    active=False
    try:
        fixture=call('/bbs-export-fixture',{'audio':name})
        active=True
        dismiss_onboarding()
        call('/wait',{'ticks':10})
        call('/screenshot',{'name':'export-audio-original-editor'})
        sys.argv=['export_smoke','--film',fixture['dataId']]
        export_smoke.main()
        path=report_path('forge1122-export-smoke.json')
        report=json.loads(path.read_text(encoding='utf-8'))
        movie=report['movie']
        metadata=json.loads(subprocess.check_output([export_smoke.executable('ffprobe'),'-v','error','-select_streams','a:0','-show_streams','-of','json',movie],text=True,encoding='utf-8'))
        assert metadata['streams'],metadata
        pcm=subprocess.check_output([export_smoke.executable('ffmpeg'),'-v','error','-i',movie,'-vn','-ac','1','-ar',str(rate),'-f','f32le','-'],timeout=60)
        data=array.array('f');data.frombytes(pcm)
        rms=math.sqrt(sum(v*v for v in data)/len(data))
        assert rms>.03,{'silentAudio':rms}
        core=data[rate//4:rate*7//4]
        crossings=sum(1 for a,b in zip(core,core[1:]) if a<=0<b)
        frequency=crossings*rate/len(core)
        assert abs(frequency-440)<5,frequency
        report['audio']={'stream':metadata['streams'][0],'rms':rms,'frequencyHz':frequency,'seconds':len(data)/rate}
        output=report_path('forge1122-export-audio-smoke.json')
        output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print('PASS: actual export video + 440Hz audio',output)
    finally:
        if active:call('/bbs-export-fixture',{'cleanup':True})
        tone.unlink(missing_ok=True)


if __name__=='__main__':main()
