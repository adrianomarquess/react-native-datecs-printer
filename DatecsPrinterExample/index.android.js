import React, { Component } from 'react';
import { AppRegistry, StyleSheet, Text, View, Button } from 'react-native';
import DatecsPrinter from 'react-native-datecs-printer';

export default class DatecsPrinterExample extends Component {

	constructor(props) {
		super(props);
	}

	componentDidMount(){
		// DatecsPrinter.initConnection();
	}

	render() {
		return (
			<View style={styles.container}>
				
				<Button title="Ligar" onPress={() => DatecsPrinter.printSelfTest()} />

			</View>
		);
	}
}

const styles = StyleSheet.create({
	container: {
		flex: 1,
		flexDirection: 'column',
		justifyContent: 'center',
		alignItems: 'center',
		backgroundColor: '#F5FCFF',
	},
});

AppRegistry.registerComponent('DatecsPrinterExample', () => DatecsPrinterExample);
