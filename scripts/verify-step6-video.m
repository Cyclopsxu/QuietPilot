#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>
#import <Vision/Vision.h>
#import <CoreVideo/CoreVideo.h>
// Local media QA only. Requires macOS Vision / AVFoundation; does not access the phone.
int main(int argc, const char *argv[]) { @autoreleasepool {
 if(argc!=3){fprintf(stderr,"Usage: verify-step6-video INPUT.mp4 REPORT.json\n");return 1;}
 NSError *error=nil;
 AVURLAsset *asset=[AVURLAsset URLAssetWithURL:[NSURL fileURLWithPath:@(argv[1])] options:nil];
 AVAssetReader *reader=[[AVAssetReader alloc] initWithAsset:asset error:&error];
 AVAssetTrack *track=[[asset tracksWithMediaType:AVMediaTypeVideo] firstObject];
 if(!track||!reader)return 2;
 AVAssetReaderTrackOutput *output=[[AVAssetReaderTrackOutput alloc] initWithTrack:track outputSettings:@{(id)kCVPixelBufferPixelFormatTypeKey:@(kCVPixelFormatType_32BGRA)}];
 output.alwaysCopiesSampleData=NO;[reader addOutput:output];if(![reader startReading])return 3;
 NSRegularExpression *regex=[NSRegularExpression regularExpressionWithPattern:@"https?\\s*[:：]\\s*/\\s*/|sk-[A-Za-z0-9_\\-]{12,}|(?<![0-9])1[3-9][0-9]{9}(?![0-9])" options:NSRegularExpressionCaseInsensitive error:&error];
 NSMutableArray *findings=[NSMutableArray array];int frames=0,failures=0;CMSampleBufferRef sample;
 while((sample=[output copyNextSampleBuffer])) { @autoreleasepool {
  CVPixelBufferRef image=CMSampleBufferGetImageBuffer(sample);
  VNRecognizeTextRequest *request=[VNRecognizeTextRequest new];request.recognitionLevel=VNRequestTextRecognitionLevelFast;request.recognitionLanguages=@[@"en-US"];request.usesLanguageCorrection=NO;
  VNImageRequestHandler *handler=[[VNImageRequestHandler alloc] initWithCVPixelBuffer:image options:@{}];
  if(![handler performRequests:@[request] error:&error])failures++;
  for(VNRecognizedTextObservation *result in request.results) {
   NSString *text=[[result topCandidates:1] firstObject].string;
   if([regex firstMatchInString:text options:0 range:NSMakeRange(0,text.length)]) { [findings addObject:@(CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample)))];break; }
  }
  frames++;if(frames%300==0){printf("OCR frames %d\n",frames);fflush(stdout);}CFRelease(sample);
 }}
 if(reader.status==AVAssetReaderStatusFailed)return 4;
 NSDictionary *report=@{@"processedFrames":@(frames),@"ocrFailures":@(failures),@"sensitivePatternFrameTimes":findings,@"method":@"Apple Vision fast English OCR on every decoded frame; URL, credential-prefix and mainland-mobile patterns",@"scope":@"Heuristic OCR plus separately recorded visual crop review; OCR does not detect all private information"};
 NSData *data=[NSJSONSerialization dataWithJSONObject:report options:NSJSONWritingPrettyPrinted|NSJSONWritingSortedKeys error:&error];[data writeToFile:@(argv[2]) atomically:YES];
 printf("OCR complete: %d frames, %d failures, %lu flagged frames\n",frames,failures,(unsigned long)findings.count);
 return failures ? 6 : (findings.count ? 5 : 0);
}}
