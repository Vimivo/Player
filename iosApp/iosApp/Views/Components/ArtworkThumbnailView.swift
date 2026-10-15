import SwiftUI

public struct ArtworkThumbnailView: View {
    public let uri: String?
    public let kind: MediaKind
    public let size: CGFloat

    public init(uri: String?, kind: MediaKind, size: CGFloat = 48) {
        self.uri = uri
        self.kind = kind
        self.size = size
    }

    public var body: some View {
        Group {
            if let uri = uri, let url = URL(string: uri), let uiImage = UIImage(contentsOfFile: url.path) {
                Image(uiImage: uiImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else {
                ZStack {
                    LinearGradient(
                        colors: [Color.accentColor.opacity(0.8), Color.accentColor.opacity(0.4)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    Image(systemName: kind == .video ? "film" : "music.note")
                        .font(.system(size: size * 0.45, weight: .semibold))
                        .foregroundColor(.white)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size > 80 ? 16 : 8, style: .continuous))
        .shadow(color: Color.black.opacity(0.12), radius: 4, x: 0, y: 2)
    }
}
