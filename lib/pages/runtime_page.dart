import 'package:flutter/material.dart';

class RuntimePage extends StatefulWidget {
  const RuntimePage({super.key});

  @override
  State<RuntimePage> createState() => _RuntimePageState();
}

class _RuntimePageState extends State<RuntimePage> {
  @override
  Widget build(BuildContext context) {
    return const Center(child: Text('Runtime'));
  }
}
