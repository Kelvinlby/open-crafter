import 'package:flutter/material.dart';


class SettingPage extends StatefulWidget {
  const SettingPage({super.key});

  @override
  State<SettingPage> createState() => _SettingPageState();
}


class _SettingPageState extends State<SettingPage> {
  @override
  void initState() {
    super.initState();
    // init here
  }

  @override
  void dispose() {
    // dispose here
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return const Center(child: Text('Setting'));
  }
}
