import 'package:flutter/widgets.dart';

/// The single place that defines layout breakpoints (Material 3 window size classes).
class Breakpoints {
  const Breakpoints._();

  /// >= this width: navigation rail instead of bottom bar.
  static const double medium = 720;

  /// >= this width: extended (labelled) navigation rail and multi-column content.
  static const double expanded = 1100;

  /// Comfortable maximum width for reading/forms.
  static const double contentMaxWidth = 960;
  static const double formMaxWidth = 440;

  static bool isCompact(BuildContext context) => MediaQuery.sizeOf(context).width < medium;
  static bool isExpanded(BuildContext context) => MediaQuery.sizeOf(context).width >= expanded;
}
