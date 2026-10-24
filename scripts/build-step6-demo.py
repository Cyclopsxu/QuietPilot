#!/usr/bin/env python3
"""Render the reviewed Step 6 edit plan. Requires Pillow, ffmpeg and local originals.

This only edits evidence media; it neither controls the phone nor synthesizes UI.
"""
import argparse
import hashlib
import json
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[1]
SIZE = (1280, 720)
FPS = 15


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def run(args, log):
    with log.open('w') as output:
        result = subprocess.run(args, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(log.read_text()[-5000:])


def wrapped(draw, text, font, width):
    lines = []
    for paragraph in text.split('\n'):
        line = ''
        for char in paragraph:
            if line and draw.textlength(line + char, font=font) > width:
                lines.append(line)
                line = ''
            line += char
        lines.append(line)
    return lines


def card(scene, index, count, font_path, output):
    canvas = Image.new('RGB', SIZE, '#0c1526')
    draw = ImageDraw.Draw(canvas)

    def text(x, y, content, size, color, width, bottom):
        font = ImageFont.truetype(str(font_path), size)
        lines = wrapped(draw, content, font, width)
        for line in lines:
            if y + size + 6 > bottom:
                raise ValueError(f'Text exceeds card bounds: {scene["title"]}: {content}')
            draw.text((x, y), line, font=font, fill=color, stroke_width=0)
            y += size + 12

    draw.rectangle((0, 0, 1280, 7), fill='#56d9bf')
    text(48, 25, scene['section'], 20, '#56d9bf', 1080, 65)
    text(48, 65, scene['title'], 40, '#f4f7fc', 1184, 123)
    text(48, 125, scene['subtitle'], 23, '#b9c7da', 1184, 173)
    for source in scene.get('media', []):
        x, y, w, h = source['box']
        draw.rectangle((x - 2, y - 2, x + w + 2, y + h + 2), fill='#31415a')
    for block in scene.get('text', []):
        text(block['x'], block['y'], block['content'], block.get('size', 26),
             block.get('color', '#f0f4fa'), block['width'], block.get('bottom', 632))
    draw.rectangle((0, 647, 1280, 720), fill='#17233a')
    text(48, 661, scene['badge'], 20, '#ffda89', 1130, 713)
    text(1200, 661, f'{index + 1:02}/{count:02}', 16, '#b9c7da', 75, 712)
    canvas.save(output)


def stamp(seconds, srt=False):
    ms = round(seconds * 1000)
    return f'{ms // 3600000:02}:{ms // 60000 % 60:02}:{ms // 1000 % 60:02}{"," if srt else "."}{ms % 1000:03}'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, required=True)
    parser.add_argument('--plan', type=Path, default=REPO / 'docs/evidence/demo-path/video-edit-plan.json')
    parser.add_argument('--output', type=Path, default=REPO / 'docs/evidence/demo-path/step6-five-segments.mp4')
    parser.add_argument('--font', type=Path, default=Path('/System/Library/Fonts/STHeiti Medium.ttc'))
    args = parser.parse_args()
    plan = json.loads(args.plan.read_text())
    roots = {'validation': args.source_root.resolve(), 'repo': REPO}
    sources = {}
    for scene in plan['scenes']:
        for media in scene.get('media', []):
            path = (roots[media['root']] / media['path']).resolve()
            if not path.is_relative_to(roots[media['root']]):
                raise ValueError('Source escapes its declared root')
            sources[(media['root'], media['path'])] = path
    # Fail before rendering if any local original is unavailable.
    for path in sources.values():
        if not path.is_file():
            raise FileNotFoundError(path)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    timeline, subtitles = [], []
    cursor = 0
    with tempfile.TemporaryDirectory(prefix='step6-video-') as directory:
        temp = Path(directory)
        parts = []
        for index, scene in enumerate(plan['scenes']):
            print(f'Rendering {index + 1}/{len(plan["scenes"])}: {scene["title"]}', flush=True)
            background = temp / f'{index:02}.png'
            card(scene, index, len(plan['scenes']), args.font, background)
            command = ['ffmpeg', '-hide_banner', '-y', '-filter_complex_threads', '1',
                       '-loop', '1', '-framerate', str(FPS), '-i', str(background)]
            filters, previous = [], '[0:v]'
            for number, media in enumerate(scene.get('media', []), 1):
                source = sources[(media['root'], media['path'])]
                still = media['kind'] == 'still'
                if still:
                    command += ['-loop', '1', '-framerate', str(FPS)]
                command += ['-i', str(source)]
                duration = scene['duration']
                take = media.get('take', duration)
                chain = f'[{number}:v]fps={FPS},trim=start={media.get("start", 0)}:duration={take},setpts=PTS-STARTPTS'
                if media.get('crop'):
                    x, y, w, h = media['crop']
                    chain += f',crop={w}:{h}:{x}:{y}'
                x, y, w, h = media['box']
                w, h = w // 2 * 2, h // 2 * 2
                chain += f',scale={w}:{h}:force_original_aspect_ratio=decrease:force_divisible_by=2,pad={w}:{h}:(ow-iw)/2:(oh-ih)/2:black,setsar=1'
                if media.get('hideBefore'):
                    chain += f",drawbox=x=0:y=0:w=iw:h=ih:color=black:t=fill:enable='lt(t,{media['hideBefore']})'"
                if take < duration:
                    if not media.get('freeze'):
                        raise ValueError('Short media must explicitly declare its freeze')
                    chain += f',tpad=stop_mode=clone:stop_duration={duration - take}'
                chain += f'[m{number}]'
                filters += [chain, f'{previous}[m{number}]overlay={x}:{y}:shortest=1[v{number}]']
                previous = f'[v{number}]'
            if filters:
                command += ['-filter_complex', ';'.join(filters), '-map', previous]
            else:
                command += ['-map', '0:v']
            part = temp / f'{index:02}.mp4'
            command += ['-an', '-t', str(scene['duration']), '-r', str(FPS), '-c:v', 'libx264',
                        '-threads', '2', '-preset', 'fast', '-crf', '21', '-pix_fmt', 'yuv420p', str(part)]
            run(command, temp / f'{index:02}.log')
            probe = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries',
                                'format=duration:stream=width,height', '-of', 'json', str(part)]))
            if abs(float(probe['format']['duration']) - scene['duration']) > .1:
                raise ValueError(f'Empty or short source in scene {index + 1}')
            parts.append(part)
            timeline.append(dict(start=cursor, end=cursor + scene['duration'], title=scene['title'], section=scene['section']))
            subtitles.append(f'{index + 1}\n{stamp(cursor, True)} --> {stamp(cursor + scene["duration"], True)}\n{scene["title"]}\n{scene["subtitle"]}\n{scene["badge"]}\n')
            cursor += scene['duration']
        listing = temp / 'concat.txt'
        listing.write_text(''.join(f"file '{p}'\n" for p in parts))
        combined = temp / 'combined.mp4'
        run(['ffmpeg', '-hide_banner', '-y', '-f', 'concat', '-safe', '0', '-i', str(listing), '-c', 'copy', str(combined)], temp / 'concat.log')
        metadata = temp / 'chapters.txt'
        chapters = [';FFMETADATA1', 'title=ShadowAuto Step6 五段证据演示（含失败与缺失标注）']
        indexed_chapters = [dict(start=0, end=plan['chapters'][0]['start'], title='片头与证据说明')]
        indexed_chapters += plan['chapters']
        indexed_chapters += [dict(start=plan['chapters'][-1]['end'], end=cursor, title='限制与收尾')]
        for chapter in indexed_chapters:
            chapters += ['[CHAPTER]', 'TIMEBASE=1/1000', f'START={chapter["start"] * 1000}',
                         f'END={chapter["end"] * 1000}', f'title={chapter["title"]}']
        metadata.write_text('\n'.join(chapters) + '\n')
        run(['ffmpeg', '-hide_banner', '-y', '-i', str(combined), '-i', str(metadata), '-map', '0:v',
             '-map_metadata', '1', '-map_chapters', '1', '-c', 'copy', '-movflags', '+faststart', str(args.output)], temp / 'mux.log')
    args.output.with_suffix('.srt').write_text('\n'.join(subtitles))
    manifest = dict(durationSeconds=cursor, width=SIZE[0], height=SIZE[1], fps=FPS, audio=False,
                    planSha256=digest(args.plan), outputSha256=digest(args.output), timeline=timeline,
                    sources=[dict(root=root, path=name, sha256=digest(path)) for (root, name), path in sources.items()])
    args.output.with_name('video-build-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print(f'Complete: {cursor}s / {args.output.name}', flush=True)


if __name__ == '__main__':
    main()
