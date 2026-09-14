<template>
	<div class="oc-app">
		<ferrofluid-background class="oc-app__ferrofluid" />
		<div class="oc-app__content">
			<router-view></router-view>
		</div>
	</div>
</template>

<script>
	import FerrofluidBackground from '@/components/FerrofluidBackground'

	export default {
		name: 'App',
		components: {
			FerrofluidBackground
		},
		created() {
			
		}
	}
</script>

<style>
	* {
		box-sizing: border-box;
	}

	html,
	body {
		width: 100%;
		height: 100%;
		max-width: 100vw;
		margin: 0;
		padding: 0;
		overflow-x: hidden;
		background: var(--oc-bg);
		color: var(--oc-text);
		font-family: Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
	}

	#app,
	.oc-app,
	.oc-app__content {
		width: 100%;
		min-height: 100%;
		background-color: var(--oc-bg);
	}

	html::-webkit-scrollbar-corner,
	body::-webkit-scrollbar-corner {
		background: var(--oc-bg);
	}

	[v-cloak] {
		display: none
	}

	.el-tabs__item {
		font-size: 18px;
	}

	.el-loading-spinner .el-loading-text {
		color: var(--oc-text);
	}

	.el-loading-spinner .path {
		stroke: var(--oc-gold);
	}

	.el-breadcrumb {
		line-height: 60px;
		font-size: 18px;
	}

	.ql-editor {
		/*height: 500px;*/
	}

	.ql-snow .ql-tooltip[data-mode="link"]::before {
		content: "请输入链接地址:";
	}

	.ql-snow .ql-tooltip.ql-editing a.ql-action::after {
		border-right: 0px;
		content: "保存";
		padding-right: 0px;
	}

	.ql-snow .ql-tooltip[data-mode="video"]::before {
		content: "请输入视频地址:";
	}

	.ql-snow .ql-picker.ql-size .ql-picker-label::before,
	.ql-snow .ql-picker.ql-size .ql-picker-item::before {
		content: "14px";
	}

	.ql-snow .ql-picker.ql-size .ql-picker-label[data-value="small"]::before,
	.ql-snow .ql-picker.ql-size .ql-picker-item[data-value="small"]::before {
		content: "10px";
	}

	.ql-snow .ql-picker.ql-size .ql-picker-label[data-value="large"]::before,
	.ql-snow .ql-picker.ql-size .ql-picker-item[data-value="large"]::before {
		content: "18px";
	}

	.ql-snow .ql-picker.ql-size .ql-picker-label[data-value="huge"]::before,
	.ql-snow .ql-picker.ql-size .ql-picker-item[data-value="huge"]::before {
		content: "32px";
	}

	.ql-snow .ql-picker.ql-header .ql-picker-label::before,
	.ql-snow .ql-picker.ql-header .ql-picker-item::before {
		content: "文本";
	}

	.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="1"]::before,
	.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="1"]::before {
		content: "标题1";
	}

	.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="2"]::before,
	.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="2"]::before {
		content: "标题2";
	}

	.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="3"]::before,
	.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="3"]::before {
		content: "标题3";
	}

	.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="4"]::before,
	.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="4"]::before {
		content: "标题4";
	}

	.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="5"]::before,
	.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="5"]::before {
		content: "标题5";
	}

	.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="6"]::before,
	.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="6"]::before {
		content: "标题6";
	}

	.ql-snow .ql-picker.ql-font .ql-picker-label::before,
	.ql-snow .ql-picker.ql-font .ql-picker-item::before {
		content: "标准字体";
	}

	.ql-snow .ql-picker.ql-font .ql-picker-label[data-value="serif"]::before,
	.ql-snow .ql-picker.ql-font .ql-picker-item[data-value="serif"]::before {
		content: "衬线字体";
	}

	.ql-snow .ql-picker.ql-font .ql-picker-label[data-value="monospace"]::before,
	.ql-snow .ql-picker.ql-font .ql-picker-item[data-value="monospace"]::before {
		content: "等宽字体";
	}
	.breadcrumb-preview {
		border-radius: 0;
		padding: 0;
		margin: 0px auto;
		background: none;
		width: 100%;
	}
	.breadcrumb-preview .el-breadcrumb {
		padding: 0 0px;
		margin: 0 auto;
		background: none;
		width: 1200px;
		font-size: 16px;
		line-height: 40px;
		
	}
	.breadcrumb-preview .el-breadcrumb .el-breadcrumb__separator {
		margin: 0 9px;
		color: var(--oc-text-muted);
		font-weight: 500;
	}
	.breadcrumb-preview .el-breadcrumb .item1 .el-breadcrumb__inner a {
		color: var(--oc-gold);
		display: inline-block;
	}
	.breadcrumb-preview .el-breadcrumb .item2 .el-breadcrumb__inner a {
		color: var(--oc-text);
		display: inline-block;
	}
	.breadcrumb-preview .el-breadcrumb .item3 .el-breadcrumb__inner a {
		color: var(--oc-text-muted);
		display: inline-block;
	}
	.back_box {
		border-radius: 0px;
		padding: 5px calc((100% - 1200px)/2);
		margin: 10px auto 0;
		background: var(--oc-bg);
		display: flex;
		width: 100%;
		justify-content: flex-end;
	}
	.back_box .backBtn {
		border: 0;
		cursor: pointer;
		border-radius: 4px;
		padding: 0 20px;
		margin: 0;
		color: var(--oc-text);
		background: var(--oc-red);
		width: auto;
		font-size: 14px;
		line-height: 44px;
		min-height: 44px;
	}
	.back_box .backBtn .icon {
		margin: 0 4px 0 0;
		color: var(--oc-text);
		font-size: 14px;
	}
	.back_box .backBtn .text {
		color: var(--oc-text);
	}
	.back_box .backBtn:hover {
		opacity: 0.9;
	}
	.back_box .backBtn:hover .icon {
		color: var(--oc-text);
	}
	.back_box .backBtn:hover .text {
		color: #fff;
	}
	/* 分页样式 */
	#pagination {
		padding: 0 calc((100% - 1200px)/2);
		margin: 20px auto;
		overflow: hidden;
		color: #333;
		white-space: nowrap;
		font-weight: 500;
		width: 100%;
		clear: both;
		font-size: 16px;
		text-align: center;
		order: 50;
	}
	#pagination.el-pagination .el-pagination__total {
		margin: 0 10px 0 0;
		color: var(--oc-text);
		font-weight: 400;
		display: inline-block;
		vertical-align: top;
		font-size: inherit;
		line-height: 44px;
		height: 44px;
	}
	
	#pagination.el-pagination .btn-prev {
		border: none;
		border-radius: 2px;
		padding: 0 10px;
		margin: 0 5px;
		color: var(--oc-text);
		background: var(--oc-surface-2);
		display: inline-block;
		vertical-align: top;
		font-size: 16px;
		line-height: 44px;
		min-width: 44px;
		height: 44px;
	}
	
	#pagination.el-pagination .btn-next {
		border: none;
		border-radius: 2px;
		padding: 0 10px;
		margin: 0 5px;
		color: var(--oc-text);
		background: var(--oc-surface-2);
		display: inline-block;
		vertical-align: top;
		font-size: 16px;
		line-height: 44px;
		min-width: 44px;
		height: 44px;
	}
	
	#pagination.el-pagination .btn-prev:disabled {
		border: none;
		cursor: not-allowed;
		border-radius: 2px;
		padding: 0 10px;
		margin: 0 5px;
		color: var(--oc-text-muted);
		background: var(--oc-surface-2);
		display: inline-block;
		vertical-align: top;
		font-size: 16px;
		line-height: 44px;
		min-width: 44px;
		height: 44px;
	}
	
	#pagination.el-pagination .btn-next:disabled {
		border: none;
		cursor: not-allowed;
		border-radius: 2px;
		padding: 0 10px;
		margin: 0 5px;
		color: var(--oc-text-muted);
		background: var(--oc-surface-2);
		display: inline-block;
		vertical-align: top;
		font-size: 16px;
		line-height: 44px;
		min-width: 44px;
		height: 44px;
	}
	
	#pagination.el-pagination .el-pager {
		padding: 0;
		margin: 0;
		display: inline-block;
		vertical-align: top;
	}
	
	#pagination.el-pagination .el-pager .number {
		cursor: pointer;
		padding: 0 4px;
		margin: 0 5px;
		color: var(--oc-text);
		display: inline-block;
		vertical-align: top;
		font-size: 16px;
		line-height: 44px;
		border-radius: 2px;
		background: var(--oc-surface-2);
		text-align: center;
		min-width: 44px;
		height: 44px;
	}
	
	#pagination.el-pagination .el-pager .number:hover {
		cursor: pointer;
		padding: 0 4px;
		margin: 0 5px;
		color: var(--oc-bg);
		display: inline-block;
		vertical-align: top;
		font-size: 16px;
		line-height: 44px;
		border-radius: 2px;
		background: var(--oc-gold);
		text-align: center;
		min-width: 44px;
		height: 44px;
	}
	
	#pagination.el-pagination .el-pager .number.active {
		cursor: default;
		padding: 0 4px;
		margin: 0 5px;
		color: var(--oc-bg);
		display: inline-block;
		vertical-align: top;
		font-size: 16px;
		line-height: 44px;
		border-radius: 2px;
		background: var(--oc-gold);
		text-align: center;
		min-width: 44px;
		height: 44px;
	}
	
	#pagination.el-pagination .el-pagination__sizes {
		display: inline-block;
		vertical-align: top;
		font-size: 13px;
		line-height: 44px;
		height: 44px;
	}
	
	#pagination.el-pagination .el-pagination__sizes .el-input {
		margin: 0 5px;
		width: 100px;
		position: relative;
	}
	
	#pagination.el-pagination .el-pagination__sizes .el-input .el-input__inner {
		border: 1px solid var(--oc-border);
		cursor: pointer;
		padding: 0 25px 0 8px;
		color: var(--oc-text);
		display: inline-block;
		font-size: 16px;
		line-height: 44px;
		border-radius: 3px;
		outline: 0;
		background: var(--oc-surface-1);
		width: 100%;
		text-align: center;
		height: 44px;
	}
	
	#pagination.el-pagination .el-pagination__sizes .el-input span.el-input__suffix {
		top: 0;
		position: absolute;
		right: 0;
		height: 100%;
	}
	
	#pagination.el-pagination .el-pagination__sizes .el-input .el-input__suffix .el-select__caret {
		cursor: pointer;
		color: var(--oc-text-muted);
		width: 25px;
		font-size: 14px;
		line-height: 44px;
		text-align: center;
	}
	
	#pagination.el-pagination .el-pagination__jump {
		margin: 0 0 0 24px;
		color: var(--oc-text-muted);
		display: inline-block;
		vertical-align: top;
		font-size: inherit;
		line-height: 44px;
		height: 44px;
	}
	
	#pagination.el-pagination .el-pagination__jump .el-input {
		border-radius: 3px;
		padding: 0 2px;
		margin: 0 2px;
		display: inline-block;
		width: 50px;
		font-size: inherit;
		line-height: 18px;
		position: relative;
		text-align: center;
		height: 44px;
	}
	
	#pagination.el-pagination .el-pagination__jump .el-input .el-input__inner {
		border: 1px solid var(--oc-border);
		cursor: pointer;
		padding: 0 3px;
		color: var(--oc-text);
		display: inline-block;
		font-size: 16px;
		line-height: 44px;
		border-radius: 3px;
		outline: 0;
		background: var(--oc-surface-1);
		width: 100%;
		text-align: center;
		height: 44px;
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
			radial-gradient(circle at 12% 0%, rgba(200, 111, 138, .1), transparent 30%),
			radial-gradient(circle at 88% 42%, rgba(215, 161, 140, .07), transparent 26%),
			linear-gradient(180deg, rgba(18, 13, 19, .5), rgba(18, 13, 19, .86));
	}
</style>
