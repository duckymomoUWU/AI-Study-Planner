#!/usr/bin/env python3
"""Dry-run by default. Requires authenticated gh with repository + Projects access."""
import argparse
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = 'duckymomoUWU/AI-Study-Planner'
OWNER = 'duckymomoUWU'
TITLE = 'AI Study Planner — Product & Delivery'
EPICS = {'E1':'Tài khoản và dữ liệu cá nhân','E2':'Mục tiêu và kế hoạch thủ công','E3':'Đề xuất AI có kiểm soát','E4':'Công việc hôm nay và tiến độ','E5':'Chất lượng và vận hành','E6':'Kiểm chứng giá trị'}

def gh(*args, json_output=False):
    r = subprocess.run(['gh', *map(str,args)], text=True, capture_output=True)
    if r.returncode:
        raise SystemExit(f'gh {args[0]} failed (exit {r.returncode}). Check GitHub network/access; no credentials were printed.')
    return json.loads(r.stdout) if json_output else r.stdout.strip()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    stories=json.loads((ROOT/'docs/backlog.json').read_text())
    ids={s['id'] for s in stories}
    assert len(ids)==len(stories)
    assert all(s['epic'] in EPICS and set(s['dependencies']) <= ids for s in stories)
    print(f'Target: {REPO}; project: {TITLE}; {len(EPICS)} epics + {len(stories)} stories')
    if not args.apply:
        for s in stories: print(f"{s['id']} [{s['epic']}, {s['priority']}, {s['sprint']}] {s['title']}")
        print('Dry run only. Use --apply on an authenticated machine; see docs/team-board.md.')
        return
    # Verify identity and Projects capability before creating anything.
    gh('api','user','--jq','.login')
    projects=gh('project','list','--owner',OWNER,'--limit','1000','--format','json',json_output=True)['projects']
    if len(projects)>=1000: raise SystemExit('Project discovery limit reached; inspect manually before importing.')
    matches=[p for p in projects if p['title']==TITLE]
    if len(matches)>1: raise SystemExit('Multiple matching projects; resolve names before importing.')
    project=matches[0] if matches else gh('project','create','--owner',OWNER,'--title',TITLE,'--format','json',json_output=True)
    number=project['number']; project_id=project['id']
    gh('project','link',number,'--owner',OWNER,'--repo',REPO)
    fields=gh('project','field-list',number,'--owner',OWNER,'--limit','100','--format','json',json_output=True)['fields']
    field_map={f['name']:f for f in fields}
    choices={'Priority':['Must','Should','Could'],'Epic':list(EPICS),'Target Sprint':['Sprint 1','Sprint 2','Sprint 3','Unscheduled']}
    for name,options in choices.items():
        if name not in field_map:
            field_map[name]=gh('project','field-create',number,'--owner',OWNER,'--name',name,'--data-type','SINGLE_SELECT','--single-select-options',','.join(options),'--format','json',json_output=True)
        if not set(options)<={o['name'] for o in field_map[name].get('options',[])}:
            raise SystemExit(f'Existing field {name} has incompatible options; preserve and reconcile in UI.')
    labels=gh('label','list','--repo',REPO,'--limit','1000','--json','name',json_output=True)
    if len(labels)>=1000: raise SystemExit('Label discovery limit reached.')
    existing_labels={x['name'] for x in labels}
    for label,color in [('epic','5319e7'),('story','0075ca'),('bug','d73a4a'),('impediment','b60205'),('improvement','0e8a16')]:
        if label not in existing_labels: gh('label','create',label,'--repo',REPO,'--color',color)
    issues=gh('issue','list','--repo',REPO,'--state','all','--limit','10000','--json','number,title,body,url',json_output=True)
    if len(issues)>=10000: raise SystemExit('Issue discovery limit reached; inspect manually before importing.')
    def ensure_issue(key,title,body,label):
        marker=f'<!-- ai-study-planner:{key} -->'
        matches=[i for i in issues if marker in (i.get('body') or '')]
        if len(matches)>1: raise SystemExit(f'Duplicate marker {key}; resolve manually.')
        if matches: return matches[0]
        with tempfile.NamedTemporaryFile(mode='w',suffix='.md',encoding='utf-8') as f:
            f.write(body+'\n\n'+marker+'\n'); f.flush()
            url=gh('issue','create','--repo',REPO,'--title',title,'--body-file',f.name,'--label',label)
        i={'url':url,'body':marker}; issues.append(i); return i
    epic_urls={}
    for key,title in EPICS.items():
        i=ensure_issue(key,f'[{key}] {title}','Epic thuộc Product Goal: lập lịch ôn khả thi, người học kiểm soát. Các story bên dưới liên kết về Epic này. Hoàn tất khi các story Must thuộc phạm vi release đạt DoD.','epic')
        epic_urls[key]=i['url']
        gh('project','item-add',number,'--owner',OWNER,'--url',i['url'])
    story_urls={}
    # File is ordered so dependencies exist before dependents; fail if this changes.
    for s in stories:
        dependencies='\n'.join('- '+story_urls[d] for d in s['dependencies']) or 'Không'
        body=f"{s['story']}\n\nEpic: {epic_urls[s['epic']]}\nPriority: {s['priority']}\nSprint dự kiến: {s['sprint']}\nƯớc lượng: Developers xác định\n\n## Acceptance criteria\n"+'\n'.join('- [ ] '+a for a in s['acceptance'])+'\n\n## Dependencies\n'+dependencies+'\n\nDefinition of Done: docs/team-board.md. Chưa gán assignee; Developers tự chọn khi Planning.'
        i=ensure_issue(s['id'],f"[{s['id']}] {s['title']}",body,'story'); story_urls[s['id']]=i['url']
        item=gh('project','item-add',number,'--owner',OWNER,'--url',i['url'],'--format','json',json_output=True)
        for name,value in [('Priority',s['priority']),('Epic',s['epic']),('Target Sprint',s['sprint'])]:
            field=field_map[name]; option=next(o['id'] for o in field['options'] if o['name']==value)
            gh('project','item-edit','--id',item['id'],'--project-id',project_id,'--field-id',field['id'],'--single-select-option-id',option)
    print(f'Imported. Open https://github.com/users/{OWNER}/projects/{number}; finish UI setup in docs/team-board.md.')

if __name__=='__main__': main()
