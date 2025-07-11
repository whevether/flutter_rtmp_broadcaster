#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint rtmp_streaming.podspec' to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'rtmp_streaming'
  s.version          = '0.0.2'
  s.summary          = 'FLutter plugin to allow rtmp to work with ios.'
  s.description      = <<-DESC
A new flutter plugin project.
                       DESC
  s.homepage         = 'https://github.com/whevether/flutter_rtmp_broadcaster'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'WhelkSoft' => 'pinkfish@gmail.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.dependency 'HaishinKit', '~> 1.6.0'
  s.platform = :ios, '13.0'

  # Flutter.framework does not contain a i386 slice. Only x86_64 simulators are supported.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'VALID_ARCHS[sdk=iphonesimulator*]' => 'x86_64' }
  s.swift_version = '6.1.1'
end
