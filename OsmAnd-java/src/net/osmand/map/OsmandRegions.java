package net.osmand.map;


import gnu.trove.list.array.TIntArrayList;
import net.osmand.ResultMatcher;
import net.osmand.binary.BinaryMapDataObject;
import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.data.QuadRect;
import net.osmand.data.QuadTree;
import net.osmand.util.MapAlgorithms;
import net.osmand.util.MapUtils;

import java.io.*;
import java.util.*;

public class OsmandRegions {

	private BinaryMapIndexReader reader;
	Map<String, LinkedList<BinaryMapDataObject>> countries = new HashMap<String, LinkedList<BinaryMapDataObject>>();
	QuadTree<String> quadTree = null ;


	Integer downloadNameType = null;
	Integer prefixType = null;
	private Integer suffixType;


	public void prepareFile(String fileName) throws IOException {
		reader = new BinaryMapIndexReader(new RandomAccessFile(fileName, "r"));
	}

	public boolean containsCountry(String name){
		return countries.containsKey(name);
	}

	public String getDownloadName(BinaryMapDataObject o) {
		if(downloadNameType == null) {
			return null;
		}
		return o.getNameByType(downloadNameType);
	}

	public String getPrefix(BinaryMapDataObject o) {
		if(prefixType == null) {
			return null;
		}
		return o.getNameByType(prefixType);
	}

	public String getSuffix(BinaryMapDataObject o) {
		if(suffixType == null) {
			return null;
		}
		return o.getNameByType(suffixType);
	}

	public boolean isInitialized(){
		return reader != null;
	}



	public boolean contain(BinaryMapDataObject bo, int tx, int ty) {
		int t = 0;
		for (int i = 1; i < bo.getPointsLength(); i++) {
			int fx = MapAlgorithms.ray_intersect_x(bo.getPoint31XTile(i - 1),
					bo.getPoint31YTile(i - 1),
					bo.getPoint31XTile(i),
					bo.getPoint31YTile(i), ty);
			if (Integer.MIN_VALUE != fx && tx >= fx) {
				t++;
			}
		}
		return t % 2 == 1;
	}

	private List<BinaryMapDataObject> getCountries(int tile31x, int tile31y) {
		HashSet<String> set = new HashSet<String>(quadTree.queryInBox(new QuadRect(tile31x, tile31y, tile31x, tile31y),
				new ArrayList<String>()));
		List<BinaryMapDataObject> result = new ArrayList<BinaryMapDataObject>();
		Iterator<String> it = set.iterator();

		while (it.hasNext()) {
			String cname = it.next();
			BinaryMapDataObject container = null;
			int count = 0;
			for (BinaryMapDataObject bo : countries.get(cname)) {
				if (contain(bo, tile31x, tile31y)) {
					count++;
					container = bo;
					break;
				}
			}
			if (count % 2 == 1) {
				result.add(container);
			}
		}
		return result;
	}


	public List<BinaryMapDataObject> query(final int tile31x, final int tile31y) throws IOException {
		if(quadTree != null) {
			return getCountries(tile31x, tile31y);
		}
		return queryNoInit(tile31x, tile31y);
	}

	private List<BinaryMapDataObject> queryNoInit(final int tile31x, final int tile31y) throws IOException {
		final List<BinaryMapDataObject> result = new ArrayList<BinaryMapDataObject>();
		BinaryMapIndexReader.SearchRequest<BinaryMapDataObject> sr = BinaryMapIndexReader.buildSearchRequest(tile31x, tile31x, tile31y, tile31y,
				5, new BinaryMapIndexReader.SearchFilter() {
					@Override
					public boolean accept(TIntArrayList types, BinaryMapIndexReader.MapIndex index) {
						return true;
					}
				}, new ResultMatcher<BinaryMapDataObject>() {


					@Override
					public boolean publish(BinaryMapDataObject object) {
						if (object.getPointsLength() < 1) {
							return false;
						}
						initTypes(object);
						if (contain(object, tile31x, tile31y)) {
							result.add(object);
						}
						return false;
					}

					@Override
					public boolean isCancelled() {
						return false;
					}
				}
		);
		if(reader != null) {
			reader.searchMapIndex(sr);
		}
		return result;
	}


	public List<BinaryMapDataObject> queryBbox(int lx, int rx, int ty, int by) throws IOException {
		final List<BinaryMapDataObject> result = new ArrayList<BinaryMapDataObject>();
		BinaryMapIndexReader.SearchRequest<BinaryMapDataObject> sr = BinaryMapIndexReader.buildSearchRequest(lx, rx, ty, by,
				5, new BinaryMapIndexReader.SearchFilter() {
					@Override
					public boolean accept(TIntArrayList types, BinaryMapIndexReader.MapIndex index) {
						return true;
					}
				}, new ResultMatcher<BinaryMapDataObject>() {

					@Override
					public boolean publish(BinaryMapDataObject object) {
						if (object.getPointsLength() < 1) {
							return false;
						}
						initTypes(object);
						result.add(object);
						return false;
					}

					@Override
					public boolean isCancelled() {
						return false;
					}
				}
		);
		if(reader != null) {
			reader.searchMapIndex(sr);
		}
		return result;
	}


	public void cacheAllCountries() throws IOException {
		quadTree = new QuadTree<String>(new QuadRect(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE),
				8, 0.55f);
		BinaryMapIndexReader.SearchRequest<BinaryMapDataObject> sr = BinaryMapIndexReader.buildSearchRequest(0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE,
				5, new BinaryMapIndexReader.SearchFilter() {
					@Override
					public boolean accept(TIntArrayList types, BinaryMapIndexReader.MapIndex index) {
						return true;
					}
				}, new ResultMatcher<BinaryMapDataObject>() {


					@Override
					public boolean publish(BinaryMapDataObject object) {
						if (object.getPointsLength() < 1) {
							return false;
						}
						initTypes(object);
						String nm = object.getNameByType(downloadNameType);
						if (!countries.containsKey(nm)) {
							LinkedList<BinaryMapDataObject> ls = new LinkedList<BinaryMapDataObject>();
							countries.put(nm, ls);
							ls.add(object);
						} else {
							countries.get(nm).add(object);
						}

						int maxx = object.getPoint31XTile(0);
						int maxy = object.getPoint31YTile(0);
						int minx = maxx;
						int miny = maxy;
						for (int i = 1; i < object.getPointsLength(); i++) {
							int x = object.getPoint31XTile(i);
							int y = object.getPoint31YTile(i);
							if (y < miny) {
								miny = y;
							} else if (y > maxy) {
								maxy = y;
							}
							if (x < minx) {
								minx = x;
							} else if (x > maxx) {
								maxx = x;
							}
						}
						quadTree.insert(nm, new QuadRect(minx, miny, maxx, maxy));
						return false;
					}

					@Override
					public boolean isCancelled() {
						return false;
					}
				}
		);
		if(reader != null) {
			reader.searchMapIndex(sr);
		}
	}

	private void initTypes(BinaryMapDataObject object) {
		if (downloadNameType == null) {
			downloadNameType = object.getMapIndex().getRule("download_name", null);
			prefixType = object.getMapIndex().getRule("region_prefix", null);
			suffixType = object.getMapIndex().getRule("region_suffix", null);
			if (downloadNameType == null) {
				throw new IllegalStateException();
			}
		}
	}


	private static void testCountry(OsmandRegions or, double lat, double lon, String... test) throws IOException {
		long t = System.currentTimeMillis();
		List<BinaryMapDataObject> cs = or.query(MapUtils.get31TileNumberX(lon), MapUtils.get31TileNumberY(lat));
		Set<String> expected = new TreeSet<String>(Arrays.asList(test));
		Set<String> found = new TreeSet<String>();
			for(BinaryMapDataObject b : cs) {
				found.add(b.getName());
			}

		if(!found.equals(expected)) {
			throw new IllegalStateException(" Expected " + expected + " but was " + found);
		}
		System.out.println("Found " + expected + " in " + (System.currentTimeMillis() - t) + " ms");
	}


	public static void main(String[] args) throws IOException {
		OsmandRegions or = new OsmandRegions();
		or.prepareFile("/home/victor/projects/osmand/osm-gen/Osmand_regions.obf");

//		long t = System.currentTimeMillis();
//		or.cacheAllCountries();
//		System.out.println("Init " + (System.currentTimeMillis() - t));

		//testCountry(or, 15.8, 23.09, "chad");
		testCountry(or, 52.10, 4.92, "netherlands");
		testCountry(or, 52.15, 7.50, "nordrhein-westfalen");
		testCountry(or, 40.0760, 9.2807, "italy");
		testCountry(or, 28.8056, 29.9858, "africa", "egypt" );
		testCountry(or, 35.7521, 139.7887, "japan");
		testCountry(or, 46.5145, 102.2580, "mongolia");
		testCountry(or, 62.54, 43.36, "arkhangelsk", "northwestern-federal-district");


	}
}
