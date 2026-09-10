// Offline macOS H.264 export and decoded-frame verification.
// swift encode.swift <frames-directory> <new-output.mp4>
import Foundation
import AVFoundation
import AppKit
import CoreVideo
let frames = CommandLine.arguments[1]
let output = URL(fileURLWithPath: CommandLine.arguments[2])
let width=1672, height=940, fps:Int32=24, count=192
guard !FileManager.default.fileExists(atPath:output.path) else { fatalError("Output exists; choose a new version") }
let writer=try AVAssetWriter(outputURL:output,fileType:.mp4)
let input=AVAssetWriterInput(mediaType:.video,outputSettings:[AVVideoCodecKey:AVVideoCodecType.h264,AVVideoWidthKey:width,AVVideoHeightKey:height,AVVideoCompressionPropertiesKey:[AVVideoAverageBitRateKey:10000000,AVVideoProfileLevelKey:AVVideoProfileLevelH264HighAutoLevel,AVVideoMaxKeyFrameIntervalKey:24]])
input.expectsMediaDataInRealTime=false
let attrs:[String:Any]=[kCVPixelBufferPixelFormatTypeKey as String:kCVPixelFormatType_32ARGB,kCVPixelBufferWidthKey as String:width,kCVPixelBufferHeightKey as String:height,kCVPixelBufferCGImageCompatibilityKey as String:true,kCVPixelBufferCGBitmapContextCompatibilityKey as String:true]
let adaptor=AVAssetWriterInputPixelBufferAdaptor(assetWriterInput:input,sourcePixelBufferAttributes:attrs)
writer.add(input)
guard writer.startWriting() else { fatalError("Cannot start writer: \(String(describing:writer.error))") }
writer.startSession(atSourceTime:.zero)
for i in 0..<count {
  while !input.isReadyForMoreMediaData { if writer.status == .failed { fatalError("Encoder failed") }; Thread.sleep(forTimeInterval:0.005) }
  try autoreleasepool {
    let url=URL(fileURLWithPath:frames).appendingPathComponent(String(format:"%04d.png",i))
    guard let data=try? Data(contentsOf:url),let image=NSBitmapImageRep(data:data)?.cgImage else { fatalError("Missing frame \(i)") }
    var pixel:CVPixelBuffer?
    guard CVPixelBufferPoolCreatePixelBuffer(nil,adaptor.pixelBufferPool!,&pixel)==kCVReturnSuccess,let buffer=pixel else { fatalError("Pixel allocation failed") }
    CVPixelBufferLockBaseAddress(buffer,[])
    let ctx=CGContext(data:CVPixelBufferGetBaseAddress(buffer),width:width,height:height,bitsPerComponent:8,bytesPerRow:CVPixelBufferGetBytesPerRow(buffer),space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGImageAlphaInfo.noneSkipFirst.rawValue)!
    ctx.draw(image,in:CGRect(x:0,y:0,width:width,height:height))
    CVPixelBufferUnlockBaseAddress(buffer,[])
    guard adaptor.append(buffer,withPresentationTime:CMTime(value:Int64(i),timescale:fps)) else { fatalError("Append failed") }
  }
}
input.markAsFinished()
writer.endSession(atSourceTime:CMTime(value:Int64(count),timescale:fps))
let done=DispatchSemaphore(value:0)
writer.finishWriting { done.signal() }
done.wait()
guard writer.status == .completed else { fatalError("Export failed: \(String(describing:writer.error))") }
let asset=AVURLAsset(url:output)
let generator=AVAssetImageGenerator(asset:asset)
generator.requestedTimeToleranceBefore = .zero
generator.requestedTimeToleranceAfter = .zero
for frame in [0,84,191] {
  let image=try generator.copyCGImage(at:CMTime(value:Int64(frame),timescale:fps),actualTime:nil)
  let bitmap=NSBitmapImageRep(cgImage:image)
  try bitmap.representation(using:.png,properties:[:])!.write(to:output.deletingLastPathComponent().appendingPathComponent("decoded-\(frame).png"))
}
print("Saved \(output.path); \(CMTimeGetSeconds(asset.duration)) seconds; \(width)x\(height); \(fps) fps; \(asset.tracks(withMediaType:.audio).count) audio tracks")
