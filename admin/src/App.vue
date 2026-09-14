<template>
		<div id="app" class="oc-app">
		<ferrofluid-background class="oc-app__ferrofluid" />
		<div class="oc-app__content">
			<router-view></router-view>
		</div>
	</div>
</template>

<script>
	import FerrofluidBackground from '@/components/common/FerrofluidBackground'

	export default {
		name: 'app',
		components: {
			FerrofluidBackground
		},
		created() {
			
		}
	}
</script>

<style>

	* {
		padding: 0;
		margin: 0;
		box-sizing: border-box;
	}

		html,
		body {
			width: 100%;
			height: 100%;
			max-width: 100vw;
			overflow-x: hidden;
			background: var(--oc-bg);
			color: var(--oc-text);
		}

		#app {
			width: 100%;
			min-height: 100%;
			background: var(--oc-bg);
		}

		body {
			padding: 0;
			margin: 0;
		}

		html::-webkit-scrollbar-corner,
		body::-webkit-scrollbar-corner {
			background: var(--oc-bg);
		}

	.oc-app {
		position: relative;
		min-height: 100vh;
		isolation: isolate;
		background: var(--oc-bg);
	}

	.oc-app__ferrofluid {
		z-index: 0;
	}

	.oc-app__content {
		position: relative;
		z-index: 1;
		min-height: 100vh;
		background:
			radial-gradient(circle at 88% 8%, rgba(200, 111, 138, .1), transparent 28%),
			radial-gradient(circle at 8% 82%, rgba(215, 161, 140, .07), transparent 26%),
			linear-gradient(180deg, rgba(18, 13, 19, .58), rgba(18, 13, 19, .88));
	}
</style>
