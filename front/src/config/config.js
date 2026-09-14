export default {
	baseUrl: process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080/face/',
	name: process.env.VUE_APP_API_CONTEXT || '/face',
	indexNav: [
		{
				name: '护理套餐',
			url: '/index/xinnengyuanqiche',
		},
		{
				name: '美容项目',
			url: '/index/shouhoufuwu',
		},
		{
				name: '护理建议',
			url: '/index/guzhangpaicha',
		},
	],
	cateList: [
		{
				name: '美容项目',
			refTable: 'fuwufenlei',
			refColumn: 'fuwufenlei',
		},
	]
}
