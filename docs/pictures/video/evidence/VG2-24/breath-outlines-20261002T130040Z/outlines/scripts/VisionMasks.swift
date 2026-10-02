// Headless, disposable raster preparation helper. This is not a video app or backend.
// Runtime/generation and finishing continue through the production Kotlin ComfyUI owners.
import Foundation
import Vision
import CoreImage
import ImageIO

struct MaskInput: Decodable {
    let source: String
    let output: String
    let allInstances: Bool
    let seeds: [[Double]]
}
let args = CommandLine.arguments
guard args.count == 2 else { fatalError("One input manifest is required") }
let inputs = try JSONDecoder().decode([MaskInput].self, from: Data(contentsOf: URL(fileURLWithPath: args[1])))
let context = CIContext(options: [.cacheIntermediates: false])
let colors = CGColorSpaceCreateDeviceGray()
for input in inputs {
    try autoreleasepool {
        let output = URL(fileURLWithPath: input.output)
        guard !FileManager.default.fileExists(atPath: output.path) else { fatalError("Refusing an existing output") }
        try FileManager.default.createDirectory(at: output, withIntermediateDirectories: false)
        let url = URL(fileURLWithPath: input.source)
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else { fatalError("Missing image") }
        let request = VNGenerateForegroundInstanceMaskRequest()
        request.revision = VNGenerateForegroundInstanceMaskRequestRevision1
        let handler = VNImageRequestHandler(cgImage: image, orientation: .up, options: [:])
        try handler.perform([request])
        guard let observation = request.results?.first else { fatalError("No foreground observation") }
        let labels = observation.instanceMask
        CVPixelBufferLockBaseAddress(labels, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(labels, .readOnly) }
        guard CVPixelBufferGetPixelFormatType(labels) == kCVPixelFormatType_OneComponent8,
              let base = CVPixelBufferGetBaseAddress(labels) else { fatalError("Unexpected instance label format") }
        let width = CVPixelBufferGetWidth(labels), height = CVPixelBufferGetHeight(labels)
        let rowBytes = CVPixelBufferGetBytesPerRow(labels)
        let bytes = base.assumingMemoryBound(to: UInt8.self)
        var selected = IndexSet()
        var sampled = [Int]()
        for point in input.seeds {
            guard point.count == 2, point.allSatisfy({ $0 >= 0 && $0 < 1 }) else { fatalError("Invalid seed") }
            let x = Int(point[0] * Double(width)), y = Int(point[1] * Double(height))
            let label = Int(bytes[y * rowBytes + x]); sampled.append(label)
            guard label != 0, observation.allInstances.contains(label) else { fatalError("Foreground seed selects background") }
            selected.insert(label)
        }
        let sets: [(String,IndexSet)] = input.allInstances
            ? observation.allInstances.map { ("instance-\($0)", IndexSet(integer: $0)) }
            : [("mask",selected)]
        for (name, instances) in sets {
            guard !instances.isEmpty else { fatalError("No selected foreground instance") }
            let mask = try observation.generateScaledMaskForImage(forInstances: instances, from: handler)
            let ci = CIImage(cvPixelBuffer: mask)
            guard Int(ci.extent.width) == image.width, Int(ci.extent.height) == image.height else { fatalError("Mask crop or dimensions changed") }
            try context.writePNGRepresentation(of: ci, to: output.appendingPathComponent(name + ".png"), format: .L8, colorSpace: colors)
        }
        let receipt: [String:Any] = ["source": input.source, "dimensions": [image.width,image.height],
            "instances": Array(observation.allInstances), "selected": Array(selected), "sampledSeeds": sampled,
            "revision": request.revision, "cropped": false]
        try JSONSerialization.data(withJSONObject: receipt, options: [.prettyPrinted,.sortedKeys]).write(to: output.appendingPathComponent("instances.json"))
        print(output.path)
    }
}
