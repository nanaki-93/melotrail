from pathlib import Path
import json,hashlib,uuid
from PIL import Image
run=Path(__file__).resolve().parents[1]
assets=Path.cwd()/'docs/pictures/video/tabi-assets/train-actions/breath-outlines-20261002T130040Z'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
backing=assets/'fixed-cabin-cup-free-v2-1080p.png';batches=[]
for index,(start,end) in enumerate([(1,33),(33,65),(65,97),(97,130)]):
    frames=[Image.open(assets/'drink/cup-support'/f'rgba-{n:04d}.png').convert('RGBA') for n in range(start,end)]
    source=run/'inputs'/f'layer-{index}.png'
    assert not source.exists()
    frames[0].save(source,format='PNG',save_all=True,append_images=frames[1:],duration=40,loop=0,disposal=0,blend=0,compress_level=3)
    with Image.open(source) as im:
        assert im.n_frames==end-start and im.size==(1920,1080)
        for n in range(end-start):
            im.seek(n);assert im.convert('RGBA').tobytes()==frames[n].tobytes(), 'APNG altered a source layer'
    for im in frames:im.close()
    graph={'1':{'class_type':'LoadImage','inputs':{'image':source.name}},'2':{'class_type':'InvertMask','inputs':{'mask':['1',1]}},
        '3':{'class_type':'LoadImage','inputs':{'image':'backing.png'}},'4':{'class_type':'RepeatImageBatch','inputs':{'image':['3',0],'amount':end-start}},
        '5':{'class_type':'ImageCompositeMasked','inputs':{'destination':['4',0],'source':['1',0],'x':0,'y':0,'resize_source':False,'mask':['2',0]}},
        '9':{'class_type':'SaveAnimatedPNG','inputs':{'images':['5',0],'filename_prefix':f'fixed-cabin-{index}','fps':25,'compress_level':3}}}
    gp=run/'workflow'/f'batch-{index}.json';gp.write_text(json.dumps(graph,indent=2)+'\n')
    batches.append({'source':str(source),'sourceSha256':sha(source),'graph':str(gp),'graphSha256':sha(gp),'firstFrame':start,'frames':end-start,
        **{k:str(uuid.uuid4()) for k in ['promptId','clientId','attemptId','ownershipToken']},'fingerprint':hashlib.sha256((sha(source)+sha(gp)+sha(backing)).encode()).hexdigest()})
q={'evidence':str(run),'backing':str(backing),'backingSha256':sha(backing),'applicationSupportRoot':'/Users/marcoandreose/Library/Application Support/MelotrailVideo','batches':batches}
(run/'request.json').write_text(json.dumps(q,indent=2)+'\n')
print('Four lossless 1920x1080 alpha batches round-trip exactly; graph uses core ComfyUI masks/composition only.')
